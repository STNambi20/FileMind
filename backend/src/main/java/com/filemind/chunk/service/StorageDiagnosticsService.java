package com.filemind.chunk.service;

import com.filemind.chunk.dto.StorageCleanupResponse;
import com.filemind.chunk.dto.StorageDiagnosticsResponse;
import com.filemind.chunk.entity.StoredChunk;
import com.filemind.chunk.model.DeduplicationMetrics;
import com.filemind.chunk.model.StoredManifest;
import com.filemind.metrics.service.MetricsService;
import com.filemind.chunk.repository.StoredChunkRepository;
import com.filemind.chunk.storage.StorageProvider;
import com.filemind.version.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StorageDiagnosticsService {

    private final DeduplicationService deduplicationService;
    private final DocumentVersionRepository documentVersionRepository;
    private final StoredChunkRepository storedChunkRepository;
    private final StorageProvider storageProvider;
    private final MetricsService metricsService;

    @Transactional(readOnly = true)
    public StorageDiagnosticsResponse diagnostics() {
        DeduplicationMetrics metrics = metricsService.analyzeManifestPaths(documentVersionRepository.findAllManifestPaths());
        return toResponse(metrics, storedChunkRepository.count());
    }

    @Transactional
    public StorageCleanupResponse cleanupUnreferencedChunks() {
        Map<String, Long> referencedCounts = referencedChunkCounts(documentVersionRepository.findAllManifestPaths());
        Instant now = Instant.now();
        long removedChunkObjects = 0L;
        long removedStoredBytes = 0L;
        long correctedReferenceCounts = 0L;

        for (StoredChunk storedChunk : storedChunkRepository.findAll()) {
            long actualReferenceCount = referencedCounts.getOrDefault(storedChunk.getHash(), 0L);
            if (actualReferenceCount <= 0L) {
                storageProvider.deleteChunk(storedChunk.getStoragePath());
                storedChunkRepository.delete(storedChunk);
                removedChunkObjects += 1L;
                removedStoredBytes += storedChunk.getPhysicalSizeBytes();
                continue;
            }

            if (storedChunk.getReferenceCount() != actualReferenceCount) {
                storedChunk.setReferenceCount(actualReferenceCount);
                storedChunk.markUsed(now);
                storedChunkRepository.save(storedChunk);
                correctedReferenceCounts += 1L;
            }
        }

        return new StorageCleanupResponse(removedChunkObjects, removedStoredBytes, correctedReferenceCounts);
    }

    private Map<String, Long> referencedChunkCounts(List<String> manifestPaths) {
        Map<String, Long> counts = new HashMap<>();
        for (String manifestPath : manifestPaths) {
            if (manifestPath == null || manifestPath.isBlank()) {
                continue;
            }

            StoredManifest manifest = storageProvider.readManifest(manifestPath);
            for (var chunk : manifest.allChunks()) {
                counts.merge(chunk.hash(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private StorageDiagnosticsResponse toResponse(DeduplicationMetrics metrics, long totalStoredChunkObjects) {
        return new StorageDiagnosticsResponse(
            metrics.logicalSize(),
            metrics.actualStoredSize(),
            metrics.dedupSavedSize(),
            metrics.dedupRatio(),
            metrics.totalChunkReferences(),
            metrics.uniqueChunkCount(),
            metrics.reusedChunkReferences(),
            metrics.averageChunkSizeBytes(),
            metrics.averageStoredChunkSizeBytes(),
            metrics.compressionRatio(),
            metrics.uniqueLogicalChunkBytes(),
            totalStoredChunkObjects
        );
    }
}
