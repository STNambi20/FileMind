package com.filemind.metrics.service;

import com.filemind.chunk.model.DeduplicationMetrics;
import com.filemind.chunk.service.DeduplicationService;
import com.filemind.document.dto.DocumentStorageStats;
import com.filemind.version.entity.DocumentVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final DeduplicationService deduplicationService;

    @Transactional(readOnly = true)
    public DeduplicationMetrics analyzeManifestPaths(Collection<String> manifestPaths) {
        DeduplicationMetrics metrics = deduplicationService.analyzeManifests(manifestPaths);
        verifyConsistency(metrics);
        return metrics;
    }

    @Transactional(readOnly = true)
    public DeduplicationMetrics analyzeVersions(Collection<DocumentVersion> versions) {
        return analyzeManifestPaths(versions.stream().map(DocumentVersion::getManifestPath).toList());
    }

    public DocumentStorageStats toDocumentStorageStats(DeduplicationMetrics metrics) {
        verifyConsistency(metrics);
        return new DocumentStorageStats(
            metrics.logicalSize(),
            metrics.actualStoredSize(),
            metrics.dedupSavedSize(),
            metrics.dedupRatio(),
            metrics.totalChunkReferences(),
            metrics.uniqueChunkCount(),
            metrics.reusedChunkReferences(),
            metrics.averageChunkSizeBytes(),
            metrics.averageStoredChunkSizeBytes(),
            metrics.compressionRatio()
        );
    }

    private void verifyConsistency(DeduplicationMetrics metrics) {
        if (metrics == null) {
            throw new IllegalStateException("Storage metrics were not available.");
        }
        if (metrics.actualStoredSize() > 0 && metrics.uniqueChunkCount() <= 0) {
            throw new IllegalStateException("Stored chunk metrics are inconsistent: actual storage exists without unique chunks.");
        }
        if (metrics.logicalSize() < metrics.actualStoredSize()) {
            throw new IllegalStateException("Storage metrics are inconsistent: actual storage exceeds logical size.");
        }
    }
}
