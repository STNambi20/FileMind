package com.filemind.document.service;

import com.filemind.activity.dto.ActivityItemResponse;
import com.filemind.activity.entity.ActivityAction;
import com.filemind.activity.service.ActivityService;
import com.filemind.collaboration.dto.CollaboratorResponse;
import com.filemind.collaboration.service.DocumentAccessService;
import com.filemind.common.exception.BadRequestException;
import com.filemind.common.util.TextContentSanitizer;
import com.filemind.document.dto.DocumentStorageStats;
import com.filemind.document.dto.DocumentViewerResponse;
import com.filemind.document.entity.Document;
import com.filemind.document.repository.DocumentRepository;
import com.filemind.settings.service.UserPreferenceService;
import com.filemind.user.entity.User;
import com.filemind.version.dto.VersionComparisonResponse;
import com.filemind.version.dto.VersionSummaryResponse;
import com.filemind.version.entity.DocumentVersion;
import com.filemind.version.mapper.VersionResponseMapper;
import com.filemind.version.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DocumentViewerService {

    private static final int MAX_PREVIEW_CHARACTERS = 120_000;
    private static final int MAX_DIFF_PREVIEW_ITEMS = 8;
    private static final DateTimeFormatter VERSION_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
        .withLocale(Locale.ENGLISH)
        .withZone(ZoneId.systemDefault());

    private final DocumentAccessService documentAccessService;
    private final DocumentSnapshotService documentSnapshotService;
    private final VersionService versionService;
    private final VersionResponseMapper versionResponseMapper;
    private final ActivityService activityService;
    private final DocumentRepository documentRepository;
    private final UserPreferenceService userPreferenceService;

    @Transactional
    public DocumentViewerResponse open(User user, String documentId, String versionId) {
        Document document = documentAccessService.requireReadableDocument(user, documentId);
        DocumentAccessService.AccessContext access = documentAccessService.getAccessContext(user, document);
        DocumentSnapshotService.DocumentSnapshot snapshot = documentSnapshotService.load(document);
        DocumentVersion selectedVersion = resolveVersion(
            user,
            documentId,
            versionId,
            snapshot.currentVersion(),
            snapshot.completedVersions().isEmpty() ? null : snapshot.completedVersions().get(0)
        );

        recordView(user, document, selectedVersion);

        List<CollaboratorResponse> collaborators = access.canManageAccess()
            ? documentAccessService.listCollaborators(document)
            : List.of();
        PreviewContent preview = buildPreview(selectedVersion);
        List<ActivityItemResponse> recentActivity = activityService.recentDocumentActivity(documentId);

        return new DocumentViewerResponse(
            document.getId(),
            document.getName(),
            document.getOwner().getFullName(),
            document.getOwner().getEmail(),
            access.permission().name(),
            access.canEdit(),
            access.canManageAccess(),
            document.getLastViewedAt(),
            document.getLastViewedByName(),
            document.getLastViewedByEmail(),
            snapshot.storageStats(),
            snapshot.totalVersions(),
            versionResponseMapper.toSummary(selectedVersion),
            preview.mode(),
            preview.text(),
            preview.truncated(),
            collaborators,
            recentActivity
        );
    }

    @Transactional
    public VersionComparisonResponse compare(User user, String documentId, String baseVersionId, String targetVersionId) {
        documentAccessService.requireReadableDocument(user, documentId);
        DocumentVersion baseVersion = resolveVersionForCompare(documentId, baseVersionId, "base");
        DocumentVersion targetVersion = resolveVersionForCompare(documentId, targetVersionId, "target");

        VersionSummaryResponse baseSummary = versionResponseMapper.toSummary(baseVersion);
        VersionSummaryResponse targetSummary = versionResponseMapper.toSummary(targetVersion);

        List<String> baseLines = normalizedLines(baseVersion.getExtractedText());
        List<String> targetLines = normalizedLines(targetVersion.getExtractedText());
        if (baseLines.isEmpty() || targetLines.isEmpty()) {
            return new VersionComparisonResponse(
                baseSummary,
                targetSummary,
                false,
                "Comparison preview is available only when both versions contain extracted text.",
                0,
                0,
                0,
                List.of(),
                List.of(),
                metadataChanges(baseVersion, targetVersion)
            );
        }

        Set<String> baseSet = new LinkedHashSet<>(baseLines);
        Set<String> targetSet = new LinkedHashSet<>(targetLines);
        List<String> addedLines = targetLines.stream()
            .filter(line -> !baseSet.contains(line))
            .distinct()
            .toList();
        List<String> removedLines = baseLines.stream()
            .filter(line -> !targetSet.contains(line))
            .distinct()
            .toList();
        int sharedLines = (int) baseSet.stream().filter(targetSet::contains).count();

        return new VersionComparisonResponse(
            baseSummary,
            targetSummary,
            true,
            "Compared extracted text across the selected versions.",
            sharedLines,
            addedLines.size(),
            removedLines.size(),
            addedLines.stream().limit(MAX_DIFF_PREVIEW_ITEMS).toList(),
            removedLines.stream().limit(MAX_DIFF_PREVIEW_ITEMS).toList(),
            metadataChanges(baseVersion, targetVersion)
        );
    }

    private void recordView(User user, Document document, DocumentVersion version) {
        Instant viewedAt = Instant.now();
        documentRepository.updateViewMetadata(document.getId(), viewedAt, user.getFullName(), user.getEmail());
        document.setLastViewedAt(viewedAt);
        document.setLastViewedByName(user.getFullName());
        document.setLastViewedByEmail(user.getEmail());
        activityService.log(
            ActivityAction.DOCUMENT_VIEWED,
            user,
            document,
            version,
            "Opened version " + version.getVersionNumber() + " in the viewer"
        );
    }

    private DocumentVersion resolveVersion(User user,
                                          String documentId,
                                          String versionId,
                                          DocumentVersion currentVersion,
                                          DocumentVersion latestCompletedVersion) {
        if (versionId == null || versionId.isBlank()) {
            DocumentVersion defaultVersion = userPreferenceService.getPreferences(user).isOpenLatestVersionAutomatically()
                ? latestCompletedVersion
                : currentVersion;
            if (defaultVersion == null) {
                throw new BadRequestException(resolveDocumentNotReadyMessage());
            }
            return defaultVersion;
        }

        DocumentVersion version = versionService.getCompletedVersionEntity(versionId);
        if (!version.getDocument().getId().equals(documentId)) {
            throw new BadRequestException("The selected version does not belong to this document.");
        }
        return version;
    }

    private DocumentVersion resolveVersionForCompare(String documentId, String versionId, String label) {
        if (versionId == null || versionId.isBlank()) {
            throw new BadRequestException("Choose a " + label + " version to compare.");
        }

        DocumentVersion version = versionService.getCompletedVersionEntity(versionId);
        if (!version.getDocument().getId().equals(documentId)) {
            throw new BadRequestException("The selected " + label + " version does not belong to this document.");
        }
        return version;
    }

    private String resolveDocumentNotReadyMessage() {
        return "This document is still processing.";
    }

    private PreviewContent buildPreview(DocumentVersion version) {
        String mediaType = version.getMediaType();
        String extractedText = version.getExtractedText();
        if ("application/pdf".equals(mediaType)) {
            return truncatePreview("PDF", extractedText);
        }
        if (mediaType != null && mediaType.startsWith("image/")) {
            return truncatePreview("IMAGE", extractedText);
        }
        if ("text/plain".equals(mediaType)
            || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mediaType)) {
            return truncatePreview("TEXT", extractedText);
        }
        return new PreviewContent("UNAVAILABLE", null, false);
    }

    private PreviewContent truncatePreview(String mode, String text) {
        text = TextContentSanitizer.stripLeadingBom(text);
        if (text == null || text.isBlank()) {
            return new PreviewContent(mode, null, false);
        }
        if (text.length() <= MAX_PREVIEW_CHARACTERS) {
            return new PreviewContent(mode, text, false);
        }
        return new PreviewContent(mode, text.substring(0, MAX_PREVIEW_CHARACTERS), true);
    }

    private List<String> normalizedLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return text.lines()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .toList();
    }

    private List<String> metadataChanges(DocumentVersion baseVersion, DocumentVersion targetVersion) {
        List<String> changes = new java.util.ArrayList<>();
        if (!baseVersion.getOrigin().equals(targetVersion.getOrigin())) {
            changes.add("Source changed from " + baseVersion.getOrigin().name() + " to " + targetVersion.getOrigin().name() + ".");
        }
        if (!baseVersion.getCreatedBy().getFullName().equals(targetVersion.getCreatedBy().getFullName())) {
            changes.add("Author changed from " + baseVersion.getCreatedBy().getFullName() + " to " + targetVersion.getCreatedBy().getFullName() + ".");
        }
        if (baseVersion.getLogicalSize() != targetVersion.getLogicalSize()) {
            changes.add("Logical size changed from " + baseVersion.getLogicalSize() + " bytes to " + targetVersion.getLogicalSize() + " bytes.");
        }
        if (baseVersion.isOcrApplied() != targetVersion.isOcrApplied()) {
            changes.add("OCR assistance changed from " + yesNo(baseVersion.isOcrApplied()) + " to " + yesNo(targetVersion.isOcrApplied()) + ".");
        }
        if (!baseVersion.getCreatedAt().equals(targetVersion.getCreatedAt())) {
            changes.add(
                "Created at changed from "
                    + VERSION_DATE_FORMATTER.format(baseVersion.getCreatedAt())
                    + " to "
                    + VERSION_DATE_FORMATTER.format(targetVersion.getCreatedAt())
                    + "."
            );
        }
        return changes;
    }

    private String yesNo(boolean value) {
        return value ? "enabled" : "not used";
    }

    private record PreviewContent(String mode, String text, boolean truncated) {
    }
}
