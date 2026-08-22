package com.filemind.document.service;

import com.filemind.activity.entity.ActivityAction;
import com.filemind.activity.service.ActivityService;
import com.filemind.comment.service.DocumentCommentService;
import com.filemind.collaboration.dto.CollaboratorResponse;
import com.filemind.collaboration.service.DocumentAccessService;
import com.filemind.common.exception.BadRequestException;
import com.filemind.common.util.FileTypeUtils;
import com.filemind.common.util.JsonListCodec;
import com.filemind.common.util.TextContentSanitizer;
import com.filemind.document.dto.DocumentDetailsResponse;
import com.filemind.document.dto.DocumentInsightMetadataResponse;
import com.filemind.document.dto.DocumentListItemResponse;
import com.filemind.document.dto.DocumentProcessingStatusResponse;
import com.filemind.document.dto.DocumentStorageStats;
import com.filemind.document.dto.RelatedDocumentResponse;
import com.filemind.document.dto.RenameDocumentRequest;
import com.filemind.document.dto.UploadDocumentResponse;
import com.filemind.document.entity.Document;
import com.filemind.document.model.ProcessingStatus;
import com.filemind.document.repository.DocumentRepository;
import com.filemind.settings.service.UserPreferenceService;
import com.filemind.share.service.PublicShareService;
import com.filemind.share.dto.ShareLinkResponse;
import com.filemind.user.entity.User;
import com.filemind.version.dto.VersionSummaryResponse;
import com.filemind.version.entity.VersionOrigin;
import com.filemind.version.entity.DocumentVersion;
import com.filemind.version.mapper.VersionResponseMapper;
import com.filemind.version.service.VersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final VersionService versionService;
    private final DocumentAccessService documentAccessService;
    private final PublicShareService publicShareService;
    private final DocumentSnapshotService documentSnapshotService;
    private final DocumentSearchService documentSearchService;
    private final RelatedDocumentService relatedDocumentService;
    private final DocumentCommentService documentCommentService;
    private final VersionResponseMapper versionResponseMapper;
    private final ActivityService activityService;
    private final JsonListCodec jsonListCodec;
    private final UploadStagingService uploadStagingService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final UserPreferenceService userPreferenceService;

    @Transactional(readOnly = true)
    public List<DocumentListItemResponse> listDocuments(User user, String query, String type) {
        List<Document> documents = documentAccessService.listAccessibleDocuments(user);
        Map<String, DocumentSnapshotService.DocumentSnapshot> snapshots = documentSnapshotService.loadAll(documents);
        boolean keepFailedUploadsVisible = userPreferenceService.getPreferences(user).isKeepFailedUploadsVisible();
        List<Document> availableDocuments = documents.stream()
            .filter(document -> {
                DocumentSnapshotService.DocumentSnapshot snapshot = snapshots.get(document.getId());
                if (snapshot == null) {
                    return false;
                }
                if (snapshot.hasVersionData()) {
                    return true;
                }
                return keepFailedUploadsVisible && document.getProcessingStatus() == ProcessingStatus.FAILED;
            })
            .toList();
        Map<String, DocumentAccessService.AccessContext> accessByDocumentId = availableDocuments.stream()
            .collect(java.util.stream.Collectors.toMap(
                Document::getId,
                document -> documentAccessService.getAccessContext(user, document),
                (left, right) -> left,
                java.util.LinkedHashMap::new
            ));

        return documentSearchService.filterAndRank(
                availableDocuments.stream().map(document -> snapshots.get(document.getId())).toList(),
                query,
                type
            )
            .stream()
            .map(document -> toListItem(
                document,
                accessByDocumentId.get(document.document().getId()),
                query
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDetailsResponse getDetails(User user, String documentId) {
        Document document = documentAccessService.requireReadableDocument(user, documentId);
        DocumentAccessService.AccessContext access = documentAccessService.getAccessContext(user, document);
        DocumentSnapshotService.DocumentSnapshot snapshot = documentSnapshotService.load(document);
        VersionSummaryResponse currentVersion = snapshot.currentVersion() == null ? null : versionResponseMapper.toSummary(snapshot.currentVersion());
        VersionSummaryResponse latestVersion = snapshot.latestVersion() == null ? null : versionResponseMapper.toSummary(snapshot.latestVersion());
        DocumentVersion metadataVersion = snapshot.currentVersion() != null ? snapshot.currentVersion() : snapshot.latestVersion();
        List<CollaboratorResponse> collaborators = access.canManageAccess()
            ? documentAccessService.listCollaborators(document)
            : List.of();
        ShareLinkResponse shareLink = access.canManageAccess()
            ? publicShareService.getShareLink(user, documentId)
            : new ShareLinkResponse(false, null, null, null, false);
        DocumentInsightMetadataResponse smartMetadata = metadataVersion == null
            ? new DocumentInsightMetadataResponse(List.of(), List.of(), List.of())
            : new DocumentInsightMetadataResponse(
                jsonListCodec.read(metadataVersion.getDatesJson()),
                jsonListCodec.read(metadataVersion.getNamesJson()),
                jsonListCodec.read(metadataVersion.getKeywordsJson())
            );
        List<RelatedDocumentResponse> relatedDocuments = snapshot.readyForViewing()
            ? relatedDocumentService.findRelated(document, documentAccessService.listAccessibleDocuments(user))
            : List.of();

        return new DocumentDetailsResponse(
            document.getId(),
            document.getName(),
            document.getOwner().getFullName(),
            document.getOwner().getEmail(),
            access.permission().name(),
            access.canEdit(),
            access.canManageAccess(),
            document.getCreatedAt(),
            document.getUpdatedAt(),
            document.getLastViewedAt(),
            document.getLastViewedByName(),
            document.getLastViewedByEmail(),
            snapshot.storageStats(),
            snapshot.totalVersions(),
            currentVersion,
            latestVersion,
            document.getProcessingStatus().name(),
            document.getProcessingMessage(),
            snapshot.readyForViewing(),
            metadataVersion != null ? TextContentSanitizer.stripLeadingBom(metadataVersion.getSummary()) : null,
            metadataVersion != null ? metadataVersion.getTitleSuggestion() : null,
            metadataVersion != null ? jsonListCodec.read(metadataVersion.getInsightsJson()) : List.of(),
            metadataVersion != null ? jsonListCodec.read(metadataVersion.getHighlightsJson()) : List.of(),
            metadataVersion != null ? metadataVersion.getReadabilityNote() : null,
            metadataVersion != null ? jsonListCodec.read(metadataVersion.getTagsJson()) : List.of(),
            metadataVersion != null ? buildExtractedPreview(metadataVersion.getExtractedText()) : null,
            smartMetadata,
            relatedDocuments,
            shareLink.url(),
            shareLink.expiresAt(),
            shareLink.expired(),
            collaborators,
            activityService.recentDocumentActivity(documentId),
            documentCommentService.list(user, documentId)
        );
    }

    @Transactional
    public UploadDocumentResponse uploadNewDocument(User user, MultipartFile file) {
        validateFile(file);
        UploadStagingService.StagedUpload stagedUpload = null;
        try {
            stagedUpload = uploadStagingService.stageForProcessing(file);
            Document document = new Document();
            document.setName(file.getOriginalFilename());
            document.setOwner(user);
            document.updateProcessingState(ProcessingStatus.UPLOADED, "Upload received. Processing has started.");
            document = documentRepository.save(document);

            DocumentVersion queuedVersion = versionService.queueUpload(
                document,
                user,
                stagedUpload.path(),
                stagedUpload.sizeBytes(),
                stagedUpload.contentHash(),
                file.getOriginalFilename(),
                FileTypeUtils.mediaTypeFor(file.getOriginalFilename(), file.getContentType()),
                "Initial upload",
                VersionOrigin.UPLOAD
            );

            applicationEventPublisher.publishEvent(new UploadQueuedEvent(queuedVersion.getId()));
            log.info("Accepted new upload for documentId={} versionId={} fileName={}", document.getId(), queuedVersion.getId(), file.getOriginalFilename());
            return new UploadDocumentResponse(
                document.getId(),
                queuedVersion.getId(),
                true,
                queuedVersion.getVersionNumber(),
                queuedVersion.getProcessingStatus().name(),
                false,
                "Upload received. Processing has started."
            );
        } catch (IOException exception) {
            if (stagedUpload != null) {
                uploadStagingService.deleteIfExists(stagedUpload.path());
            }
            throw new IllegalStateException("We couldn't read the uploaded file.", exception);
        }
    }

    @Transactional
    public UploadDocumentResponse uploadNewVersion(User user, String documentId, MultipartFile file) {
        validateFile(file);
        Document document = documentAccessService.requireEditableDocument(user, documentId);
        UploadStagingService.StagedUpload stagedUpload = null;
        try {
            stagedUpload = uploadStagingService.stageForProcessing(file);
            document.updateProcessingState(ProcessingStatus.UPLOADED, "Version upload received. Processing has started.");
            documentRepository.save(document);

            DocumentVersion queuedVersion = versionService.queueUpload(
                document,
                user,
                stagedUpload.path(),
                stagedUpload.sizeBytes(),
                stagedUpload.contentHash(),
                file.getOriginalFilename(),
                FileTypeUtils.mediaTypeFor(file.getOriginalFilename(), file.getContentType()),
                "Manual version upload",
                VersionOrigin.UPLOAD
            );

            applicationEventPublisher.publishEvent(new UploadQueuedEvent(queuedVersion.getId()));
            log.info("Accepted version upload for documentId={} versionId={} fileName={}", document.getId(), queuedVersion.getId(), file.getOriginalFilename());
            return new UploadDocumentResponse(
                document.getId(),
                queuedVersion.getId(),
                true,
                queuedVersion.getVersionNumber(),
                queuedVersion.getProcessingStatus().name(),
                true,
                "Version upload received. Processing has started."
            );
        } catch (IOException exception) {
            if (stagedUpload != null) {
                uploadStagingService.deleteIfExists(stagedUpload.path());
            }
            throw new IllegalStateException("We couldn't read the uploaded file.", exception);
        }
    }

    @Transactional
    public DocumentDetailsResponse rename(User user, String documentId, RenameDocumentRequest request) {
        Document document = documentAccessService.requireEditableDocument(user, documentId);
        document.setName(request.name().trim());
        documentRepository.save(document);
        activityService.log(ActivityAction.DOCUMENT_RENAMED, user, document, "Renamed document to " + request.name().trim());
        return getDetails(user, documentId);
    }

    @Transactional
    public void delete(User user, String documentId) {
        Document document = documentAccessService.requireOwnedDocument(user, documentId);
        versionService.deleteAllVersions(document);
        documentAccessService.deleteAllForDocument(document.getId());
        publicShareService.deleteForDocument(document.getId());
        documentRepository.delete(document);
        activityService.log(ActivityAction.DOCUMENT_DELETED, user, document, "Deleted document " + document.getName());
    }

    @Transactional
    public ResponseEntity<ByteArrayResource> downloadCurrent(User user, String documentId) {
        Document document = documentAccessService.requireReadableDocument(user, documentId);
        DocumentSnapshotService.DocumentSnapshot snapshot = documentSnapshotService.load(document);
        if (!snapshot.readyForViewing()) {
            throw new BadRequestException(document.getProcessingMessage() != null
                ? document.getProcessingMessage()
                : "This document is still processing and can't be downloaded yet.");
        }
        byte[] content = versionService.download(user, snapshot.currentVersion().getId());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + snapshot.currentVersion().getFileName() + "\"")
            .contentType(MediaType.parseMediaType(snapshot.currentVersion().getMediaType()))
            .body(new ByteArrayResource(content));
    }

    @Transactional(readOnly = true)
    public Document getReadableDocument(User user, String documentId) {
        return documentAccessService.requireReadableDocument(user, documentId);
    }

    @Transactional(readOnly = true)
    public Document getEditableDocument(User user, String documentId) {
        return documentAccessService.requireEditableDocument(user, documentId);
    }

    @Transactional(readOnly = true)
    public Document getOwnedDocument(User user, String documentId) {
        return documentAccessService.requireOwnedDocument(user, documentId);
    }

    @Transactional(readOnly = true)
    public DocumentProcessingStatusResponse getProcessingStatus(User user, String documentId) {
        Document document = documentAccessService.requireReadableDocument(user, documentId);
        DocumentSnapshotService.DocumentSnapshot snapshot = documentSnapshotService.load(document);
        VersionSummaryResponse currentVersion = snapshot.currentVersion() == null ? null : versionResponseMapper.toSummary(snapshot.currentVersion());
        return new DocumentProcessingStatusResponse(
            document.getId(),
            document.getProcessingStatus().name(),
            document.getProcessingMessage(),
            snapshot.readyForViewing(),
            currentVersion != null ? currentVersion.id() : null,
            currentVersion != null ? currentVersion.versionNumber() : null,
            snapshot.totalVersions()
        );
    }

    @Transactional
    public UploadDocumentResponse retryFailedProcessing(User user, String documentId) {
        Document document = documentAccessService.requireEditableDocument(user, documentId);
        DocumentVersion retriedVersion = versionService.retryFailedUpload(document);
        applicationEventPublisher.publishEvent(new UploadQueuedEvent(retriedVersion.getId()));
        activityService.log(ActivityAction.PROCESSING_RETRIED, user, document, retriedVersion, "Retried processing for version " + retriedVersion.getVersionNumber());
        log.info("Retried processing for documentId={} versionId={}", document.getId(), retriedVersion.getId());
        return new UploadDocumentResponse(
            document.getId(),
            retriedVersion.getId(),
            false,
            retriedVersion.getVersionNumber(),
            retriedVersion.getProcessingStatus().name(),
            false,
            document.getProcessingMessage()
        );
    }

    private DocumentListItemResponse toListItem(DocumentSnapshotService.DocumentSnapshot snapshot,
                                                DocumentAccessService.AccessContext access,
                                                String query) {
        Document document = snapshot.document();
        DocumentStorageStats storageStats = snapshot.storageStats();
        DocumentVersion displayVersion = snapshot.currentVersion() != null ? snapshot.currentVersion() : snapshot.latestVersion();
        VersionSummaryResponse displayVersionSummary = displayVersion == null ? null : versionResponseMapper.toSummary(displayVersion);

        return new DocumentListItemResponse(
            document.getId(),
            document.getName(),
            displayVersionSummary != null ? displayVersionSummary.mediaType() : "application/octet-stream",
            document.getOwner().getFullName(),
            access.permission().name(),
            access.canEdit(),
            displayVersionSummary != null ? displayVersionSummary.versionNumber() : 0,
            snapshot.totalVersions(),
            storageStats.logicalSize(),
            storageStats.actualStoredSize(),
            storageStats.dedupSavedSize(),
            storageStats.dedupRatio(),
            storageStats.totalChunkReferences(),
            storageStats.uniqueChunkCount(),
            storageStats.reusedChunkReferences(),
            document.getUpdatedAt(),
            displayVersionSummary != null ? displayVersionSummary.summary() : null,
            displayVersionSummary != null ? displayVersionSummary.tags() : List.of(),
            documentSearchService.buildSearchSnippet(snapshot, query),
            document.getProcessingStatus().name(),
            document.getProcessingMessage(),
            snapshot.readyForViewing()
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a file to upload.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !FileTypeUtils.isSupportedDocument(originalFilename)) {
            throw new BadRequestException("FileMind supports TXT, PDF, DOCX, PNG, JPG, JPEG, GIF, and BMP files.");
        }
    }

    private String buildExtractedPreview(String extractedText) {
        extractedText = TextContentSanitizer.stripLeadingBom(extractedText);
        if (extractedText == null || extractedText.isBlank()) {
            return null;
        }
        return extractedText.substring(0, Math.min(600, extractedText.length()));
    }

    public record UploadQueuedEvent(String versionId) {
    }
}
