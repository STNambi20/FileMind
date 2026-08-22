package com.filemind.version.service;

import com.filemind.activity.entity.ActivityAction;
import com.filemind.activity.service.ActivityService;
import com.filemind.ai.service.AiOrchestratorService;
import com.filemind.chunk.model.StoredContentResult;
import com.filemind.chunk.service.DeduplicationService;
import com.filemind.chunk.util.ChunkHashUtils;
import com.filemind.common.exception.BadRequestException;
import com.filemind.common.exception.ResourceNotFoundException;
import com.filemind.document.entity.Document;
import com.filemind.document.model.ProcessingStatus;
import com.filemind.document.processing.ExtractionResult;
import com.filemind.document.processing.TextExtractionService;
import com.filemind.document.service.DocumentService;
import com.filemind.settings.service.UserPreferenceService;
import com.filemind.user.entity.User;
import com.filemind.version.dto.RestoreVersionResponse;
import com.filemind.version.dto.VersionSummaryResponse;
import com.filemind.version.entity.DocumentVersion;
import com.filemind.version.entity.VersionOrigin;
import com.filemind.version.mapper.VersionResponseMapper;
import com.filemind.version.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VersionService {

    private final DocumentVersionRepository documentVersionRepository;
    private final DeduplicationService deduplicationService;
    private final TextExtractionService textExtractionService;
    private final ActivityService activityService;
    private final AiOrchestratorService aiOrchestratorService;
    private final VersionResponseMapper versionResponseMapper;
    private final UserPreferenceService userPreferenceService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public VersionCreationResult createVersion(Document document,
                                               User actor,
                                               Path contentPath,
                                               String sourceContentHash,
                                               String fileName,
                                               String mediaType,
                                               String note,
                                               VersionOrigin origin) {
        DocumentVersion currentVersion = getCompletedCurrentVersion(document.getId());
        if (currentVersion != null && currentVersionDuplicateHash(currentVersion).equals(sourceContentHash)) {
            return new VersionCreationResult(currentVersion, false);
        }

        StoredContentResult storedContentResult = null;
        try {
            storedContentResult = deduplicationService.storeContent(UUID.randomUUID().toString(), fileName, mediaType, contentPath);

            DocumentVersion version = new DocumentVersion();
            version.setDocument(document);
            version.setCreatedBy(actor);
            version.setVersionNumber(nextVersionNumber(document.getId()));
            version.setFileName(fileName);
            version.setMediaType(mediaType);
            version.setOrigin(origin);
            version.setNote(note);
            version.setSourceContentHash(sourceContentHash);
            version.setLogicalSize(storedContentResult.logicalSize());
            version = completeVersion(document, actor, currentVersion, version, storedContentResult, contentPath);
            document.updateProcessingState(ProcessingStatus.COMPLETED, null);
            document.touch();
            return new VersionCreationResult(version, true);
        } catch (RuntimeException exception) {
            if (storedContentResult != null) {
                deduplicationService.discardStoredContent(storedContentResult);
            }
            throw exception;
        }
    }

    @Transactional
    public DocumentVersion queueUpload(Document document,
                                       User actor,
                                       Path stagedContentPath,
                                       long logicalSize,
                                       String sourceContentHash,
                                       String fileName,
                                       String mediaType,
                                       String note,
                                       VersionOrigin origin) {
        DocumentVersion version = new DocumentVersion();
        version.setDocument(document);
        version.setCreatedBy(actor);
        version.setVersionNumber(nextVersionNumber(document.getId()));
        version.setFileName(fileName);
        version.setMediaType(mediaType);
        version.setOrigin(origin);
        version.setNote(note);
        version.setSourceContentHash(sourceContentHash);
        version.setLogicalSize(logicalSize);
        version.setActualStoredSize(0L);
        version.setDedupSavedSize(0L);
        version.setTotalChunkCount(0);
        version.setNewChunkCount(0);
        version.setReusedChunkCount(0);
        version.setCurrentVersion(false);
        version.setOcrApplied(false);
        version.setProcessingStatus(ProcessingStatus.UPLOADED);
        version.setProcessingError(null);
        version.setStagedContentPath(stagedContentPath.toAbsolutePath().toString());
        version.setProcessingRetryCount(0);
        version = documentVersionRepository.save(version);
        log.info("Queued upload for documentId={} versionId={} fileName={}", document.getId(), version.getId(), fileName);
        return version;
    }

    @Transactional
    public void processQueuedUpload(String versionId) {
        DocumentVersion queuedVersion = getVersionEntity(versionId);
        if (queuedVersion.getProcessingStatus().isTerminal()) {
            return;
        }

        Document document = queuedVersion.getDocument();
        Path stagedContentPath = queuedVersion.getStagedContentPath() == null ? null : Path.of(queuedVersion.getStagedContentPath());
        if (stagedContentPath == null || !Files.exists(stagedContentPath)) {
            failQueuedUpload(document, queuedVersion, "The staged upload file is no longer available.");
            return;
        }

        queuedVersion.setProcessingStatus(ProcessingStatus.PROCESSING);
        queuedVersion.setProcessingError(null);
        document.updateProcessingState(ProcessingStatus.PROCESSING, "Processing upload...");
        documentVersionRepository.save(queuedVersion);
        log.info("Started processing for documentId={} versionId={}", document.getId(), queuedVersion.getId());

        DocumentVersion currentVersion = getCompletedCurrentVersion(document.getId());
        if (currentVersion != null && currentVersionDuplicateHash(currentVersion).equals(queuedVersion.getSourceContentHash())) {
            safeDelete(stagedContentPath);
            documentVersionRepository.delete(queuedVersion);
            document.updateProcessingState(ProcessingStatus.COMPLETED, "This upload matches the current version.");
            document.touch();
            log.info("Skipped duplicate upload for document {}", document.getId());
            return;
        }

        StoredContentResult storedContentResult = null;
        try {
            storedContentResult = deduplicationService.storeContent(
                UUID.randomUUID().toString(),
                queuedVersion.getFileName(),
                queuedVersion.getMediaType(),
                stagedContentPath
            );

            completeVersion(document, queuedVersion.getCreatedBy(), currentVersion, queuedVersion, storedContentResult, stagedContentPath);
            document.updateProcessingState(
                ProcessingStatus.COMPLETED,
                queuedVersion.getVersionNumber() == 1
                    ? "Document is ready."
                    : "Version " + queuedVersion.getVersionNumber() + " is ready."
            );
            document.touch();
            safeDelete(stagedContentPath);

            if (queuedVersion.getVersionNumber() == 1) {
                activityService.log(
                    ActivityAction.DOCUMENT_CREATED,
                    queuedVersion.getCreatedBy(),
                    document,
                    queuedVersion,
                    "Uploaded " + document.getName()
                );
            } else {
                activityService.log(
                    ActivityAction.VERSION_CREATED,
                    queuedVersion.getCreatedBy(),
                    document,
                    queuedVersion,
                    "Uploaded version " + queuedVersion.getVersionNumber()
                );
            }
            if (queuedVersion.isOcrApplied()) {
                activityService.log(
                    ActivityAction.OCR_APPLIED,
                    queuedVersion.getCreatedBy(),
                    document,
                    queuedVersion,
                    "Extracted OCR text for version " + queuedVersion.getVersionNumber()
                );
            }
            log.info("Completed processing for documentId={} versionId={} versionNumber={}", document.getId(), queuedVersion.getId(), queuedVersion.getVersionNumber());
        } catch (Exception exception) {
            if (storedContentResult != null) {
                deduplicationService.discardStoredContent(storedContentResult);
            }
            String errorMessage = resolveProcessingError(exception);
            failQueuedUpload(document, queuedVersion, errorMessage);
            log.error("Upload processing failed for version {}", versionId, exception);
            if (shouldAutoRetry(queuedVersion, stagedContentPath)) {
                queueAutomaticRetry(document, queuedVersion);
            }
        }
    }

    @Transactional
    public VersionCreationResult createVersion(Document document,
                                               User actor,
                                               byte[] content,
                                               String fileName,
                                               String mediaType,
                                               String note,
                                               VersionOrigin origin) {
        Path temporaryContent = null;
        try {
            temporaryContent = Files.createTempFile("filemind-inline-", resolveSuffix(fileName));
            Files.write(temporaryContent, content);
            return createVersion(document, actor, temporaryContent, ChunkHashUtils.sha256(content), fileName, mediaType, note, origin);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to stage inline content for versioning", exception);
        } finally {
            if (temporaryContent != null) {
                try {
                    Files.deleteIfExists(temporaryContent);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Transactional
    public RestoreVersionResponse restoreVersion(User actor, String versionId) {
        DocumentVersion targetVersion = requireCompletedVersion(versionId);
        Document document = targetVersion.getDocument();
        DocumentVersion currentVersion = documentVersionRepository.findFirstByDocumentIdAndCurrentVersionTrue(document.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Current version not found"));

        if (currentVersion.getId().equals(targetVersion.getId())) {
            throw new BadRequestException("That version is already current.");
        }
        if (currentVersion.getContentHash().equals(targetVersion.getContentHash())) {
            throw new BadRequestException("That version already matches the current content.");
        }

        StoredContentResult cloned = deduplicationService.cloneManifest(targetVersion.getManifestPath(), UUID.randomUUID().toString());

        DocumentVersion restoredVersion = new DocumentVersion();
        restoredVersion.setDocument(document);
        restoredVersion.setCreatedBy(actor);
        restoredVersion.setVersionNumber(currentVersion.getVersionNumber() + 1);
        restoredVersion.setFileName(targetVersion.getFileName());
        restoredVersion.setMediaType(targetVersion.getMediaType());
        restoredVersion.setCurrentVersion(true);
        restoredVersion.setOrigin(VersionOrigin.RESTORE);
        restoredVersion.setNote("Restored from version " + targetVersion.getVersionNumber());
        restoredVersion.setExtractedText(targetVersion.getExtractedText());
        restoredVersion.setSummary(targetVersion.getSummary());
        restoredVersion.setTitleSuggestion(targetVersion.getTitleSuggestion());
        restoredVersion.setTagsJson(targetVersion.getTagsJson());
        restoredVersion.setInsightsJson(targetVersion.getInsightsJson());
        restoredVersion.setHighlightsJson(targetVersion.getHighlightsJson());
        restoredVersion.setReadabilityNote(targetVersion.getReadabilityNote());
        restoredVersion.setDatesJson(targetVersion.getDatesJson());
        restoredVersion.setNamesJson(targetVersion.getNamesJson());
        restoredVersion.setKeywordsJson(targetVersion.getKeywordsJson());
        restoredVersion.setOcrApplied(targetVersion.isOcrApplied());
        restoredVersion.setSourceContentHash(targetVersion.getSourceContentHash() == null ? targetVersion.getContentHash() : targetVersion.getSourceContentHash());
        restoredVersion.setLogicalSize(cloned.logicalSize());
        applyStoredContent(restoredVersion, cloned);
        restoredVersion.setProcessingStatus(ProcessingStatus.COMPLETED);
        document.touch();
        document.updateProcessingState(ProcessingStatus.COMPLETED, null);
        currentVersion.setCurrentVersion(false);
        documentVersionRepository.save(currentVersion);
        restoredVersion = documentVersionRepository.save(restoredVersion);

        activityService.log(
            ActivityAction.VERSION_RESTORED,
            actor,
            document,
            restoredVersion,
            "Restored version " + targetVersion.getVersionNumber() + " as version " + restoredVersion.getVersionNumber()
        );

        return new RestoreVersionResponse(
            restoredVersion.getId(),
            restoredVersion.getVersionNumber(),
            "Version restored."
        );
    }

    @Transactional(readOnly = true)
    public List<VersionSummaryResponse> listVersions(String documentId) {
        return documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId)
            .stream()
            .filter(version -> version.getProcessingStatus() == ProcessingStatus.COMPLETED)
            .map(versionResponseMapper::toSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public DocumentVersion getCurrentVersion(String documentId) {
        return documentVersionRepository.findFirstByDocumentIdAndCurrentVersionTrue(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Current version not found"));
    }

    @Transactional(readOnly = true)
    public DocumentVersion getVersionEntity(String versionId) {
        return documentVersionRepository.findById(versionId)
            .orElseThrow(() -> new ResourceNotFoundException("Version not found"));
    }

    @Transactional(readOnly = true)
    public DocumentVersion getCompletedVersionEntity(String versionId) {
        return requireCompletedVersion(versionId);
    }

    @Transactional(readOnly = true)
    public byte[] download(String versionId) {
        DocumentVersion version = requireCompletedVersion(versionId);
        return deduplicationService.reconstruct(version.getManifestPath());
    }

    @Transactional
    public byte[] download(User actor, String versionId) {
        DocumentVersion version = requireCompletedVersion(versionId);
        byte[] content = deduplicationService.reconstruct(version.getManifestPath());
        activityService.log(
            ActivityAction.DOCUMENT_DOWNLOADED,
            actor,
            version.getDocument(),
            version,
            "Downloaded version " + version.getVersionNumber()
        );
        return content;
    }

    @Transactional
    public void deleteAllVersions(Document document) {
        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(document.getId());
        for (DocumentVersion version : versions) {
            if (version.getManifestPath() != null && !version.getManifestPath().isBlank()) {
                deduplicationService.releaseManifest(version.getManifestPath());
            }
            if (version.getStagedContentPath() != null && !version.getStagedContentPath().isBlank()) {
                safeDelete(Path.of(version.getStagedContentPath()));
            }
        }
        documentVersionRepository.deleteAll(versions);
    }

    @Transactional
    public VersionCreationResult createAiRewriteVersion(Document document, User actor, String rewrittenText, String sourceLabel) {
        byte[] content = rewrittenText.getBytes(StandardCharsets.UTF_8);
        String fileName = document.getName().endsWith(".txt") ? document.getName() : document.getName() + ".txt";
        return createVersion(document, actor, content, fileName, "text/plain", "AI rewrite (" + sourceLabel + ")", VersionOrigin.AI_REWRITE);
    }

    @Transactional
    public DocumentVersion retryFailedUpload(Document document) {
        DocumentVersion latestVersion = documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(document.getId())
            .orElseThrow(() -> new ResourceNotFoundException("No version is available for retry."));

        if (latestVersion.getProcessingStatus() != ProcessingStatus.FAILED) {
            throw new BadRequestException("Only failed uploads can be retried.");
        }
        if (latestVersion.getManifestPath() != null && !latestVersion.getManifestPath().isBlank()) {
            throw new BadRequestException("This failed version already has stored content and can't be retried safely.");
        }
        if (latestVersion.getStagedContentPath() == null || latestVersion.getStagedContentPath().isBlank()) {
            throw new BadRequestException("The staged upload is no longer available. Upload the file again.");
        }

        Path stagedContentPath = Path.of(latestVersion.getStagedContentPath());
        if (!Files.exists(stagedContentPath)) {
            throw new BadRequestException("The staged upload is no longer available. Upload the file again.");
        }

        latestVersion.setProcessingStatus(ProcessingStatus.UPLOADED);
        latestVersion.setProcessingError(null);
        latestVersion.setCurrentVersion(false);
        document.updateProcessingState(ProcessingStatus.UPLOADED, "Retry requested. Processing has started.");
        document.touch();
        documentVersionRepository.save(latestVersion);
        return latestVersion;
    }

    private String resolveSuffix(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return ".tmp";
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }

    private String currentVersionDuplicateHash(DocumentVersion version) {
        if (version.getSourceContentHash() != null && !version.getSourceContentHash().isBlank()) {
            return version.getSourceContentHash();
        }
        return version.getContentHash();
    }

    private DocumentVersion completeVersion(Document document,
                                            User actor,
                                            DocumentVersion currentVersion,
                                            DocumentVersion version,
                                            StoredContentResult storedContentResult,
                                            Path contentPath) {
        ExtractionResult extractionResult = textExtractionService.extract(version.getFileName(), version.getMediaType(), contentPath);
        version.setCreatedBy(actor);
        version.setDocument(document);
        version.setExtractedText(extractionResult.text());
        version.setOcrApplied(extractionResult.ocrApplied());
        version.setStagedContentPath(null);
        version.setProcessingError(null);
        version.setProcessingStatus(ProcessingStatus.COMPLETED);
        applyStoredContent(version, storedContentResult);
        aiOrchestratorService.populateVersionMetadata(version, actor);
        if (currentVersion != null) {
            currentVersion.setCurrentVersion(false);
            documentVersionRepository.save(currentVersion);
        }
        version.setCurrentVersion(true);
        version = documentVersionRepository.save(version);
        return version;
    }

    private void applyStoredContent(DocumentVersion version, StoredContentResult storedContentResult) {
        version.setManifestPath(storedContentResult.manifestPath());
        version.setContentHash(storedContentResult.contentHash());
        version.setLogicalSize(storedContentResult.logicalSize());
        version.setActualStoredSize(storedContentResult.actualStoredSize());
        version.setDedupSavedSize(storedContentResult.dedupSavedSize());
        version.setTotalChunkCount(storedContentResult.totalChunkCount());
        version.setNewChunkCount(storedContentResult.newChunkCount());
        version.setReusedChunkCount(storedContentResult.reusedChunkCount());
    }

    private void failQueuedUpload(Document document, DocumentVersion queuedVersion, String message) {
        queuedVersion.setProcessingStatus(ProcessingStatus.FAILED);
        queuedVersion.setProcessingError(message);
        queuedVersion.setCurrentVersion(false);
        document.updateProcessingState(ProcessingStatus.FAILED, message);
        document.touch();
        documentVersionRepository.save(queuedVersion);
        log.warn("Failed processing for documentId={} versionId={} reason={}", document.getId(), queuedVersion.getId(), message);
    }

    private boolean shouldAutoRetry(DocumentVersion queuedVersion, Path stagedContentPath) {
        if (!userPreferenceService.getPreferences(queuedVersion.getCreatedBy()).isAutoRetryFailedProcessing()) {
            return false;
        }
        if (queuedVersion.getProcessingRetryCount() >= 1) {
            return false;
        }
        if (queuedVersion.getManifestPath() != null && !queuedVersion.getManifestPath().isBlank()) {
            return false;
        }
        return stagedContentPath != null && Files.exists(stagedContentPath);
    }

    private void queueAutomaticRetry(Document document, DocumentVersion queuedVersion) {
        queuedVersion.setProcessingRetryCount(queuedVersion.getProcessingRetryCount() + 1);
        queuedVersion.setProcessingStatus(ProcessingStatus.UPLOADED);
        queuedVersion.setProcessingError(null);
        document.updateProcessingState(ProcessingStatus.UPLOADED, "The first processing attempt failed. FileMind started one automatic retry.");
        document.touch();
        documentVersionRepository.save(queuedVersion);
        applicationEventPublisher.publishEvent(new DocumentService.UploadQueuedEvent(queuedVersion.getId()));
        log.info(
            "Queued automatic retry for documentId={} versionId={} retryCount={}",
            document.getId(),
            queuedVersion.getId(),
            queuedVersion.getProcessingRetryCount()
        );
    }

    private DocumentVersion getCompletedCurrentVersion(String documentId) {
        return documentVersionRepository.findFirstByDocumentIdAndCurrentVersionTrue(documentId)
            .filter(version -> version.getProcessingStatus() == ProcessingStatus.COMPLETED)
            .orElse(null);
    }

    private DocumentVersion requireCompletedVersion(String versionId) {
        DocumentVersion version = getVersionEntity(versionId);
        if (version.getProcessingStatus() != ProcessingStatus.COMPLETED || version.getManifestPath() == null || version.getManifestPath().isBlank()) {
            throw new BadRequestException(
                version.getProcessingStatus() == ProcessingStatus.FAILED
                    ? (version.getProcessingError() != null ? version.getProcessingError() : "This version failed during processing.")
                    : "This version is still processing."
            );
        }
        return version;
    }

    private int nextVersionNumber(String documentId) {
        return documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(documentId)
            .map(version -> version.getVersionNumber() + 1)
            .orElse(1);
    }

    private String resolveProcessingError(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return "The upload could not be processed.";
        }
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    private void safeDelete(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    public record VersionCreationResult(DocumentVersion version, boolean created) {
    }
}
