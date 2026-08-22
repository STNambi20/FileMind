package com.filemind.document.service;

import com.filemind.version.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DocumentUploadProcessingListener {

    private final VersionService versionService;

    @Async("documentProcessingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUploadQueued(DocumentService.UploadQueuedEvent event) {
        versionService.processQueuedUpload(event.versionId());
    }
}
