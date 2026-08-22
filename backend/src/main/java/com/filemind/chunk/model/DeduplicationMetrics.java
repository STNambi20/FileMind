package com.filemind.chunk.model;

public record DeduplicationMetrics(
    long logicalSize,
    long actualStoredSize,
    long dedupSavedSize,
    double dedupRatio,
    long totalChunkReferences,
    long uniqueChunkCount,
    long reusedChunkReferences,
    double averageChunkSizeBytes,
    double averageStoredChunkSizeBytes,
    double compressionRatio,
    long uniqueLogicalChunkBytes
) {
    public static DeduplicationMetrics empty() {
        return new DeduplicationMetrics(0L, 0L, 0L, 0D, 0L, 0L, 0L, 0D, 0D, 0D, 0L);
    }
}
