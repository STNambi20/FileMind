package com.filemind.document.service;

import com.filemind.document.model.ProcessingStatus;
import com.filemind.version.repository.DocumentVersionRepository;
import com.filemind.version.service.VersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * Upload processing happens in the background. If the desktop app or backend
 * is restarted, staged uploads must be placed back on the queue instead of
 * remaining in PROCESSING forever.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueuedUploadRecoveryService {

    private final DocumentVersionRepository versions;
    private final VersionService versionService;
    @Qualifier("documentProcessingExecutor")
    private final Executor documentProcessingExecutor;

    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterruptedUploads() {
        versions.findAll().stream()
            .filter(version -> version.getProcessingStatus() == ProcessingStatus.UPLOADED
                || version.getProcessingStatus() == ProcessingStatus.PROCESSING)
            .filter(version -> version.getStagedContentPath() != null && !version.getStagedContentPath().isBlank())
            .filter(version -> Files.exists(Path.of(version.getStagedContentPath())))
            .forEach(version -> {
                log.info("Resuming interrupted upload versionId={}", version.getId());
                documentProcessingExecutor.execute(() -> versionService.processQueuedUpload(version.getId()));
            });
    }
}
