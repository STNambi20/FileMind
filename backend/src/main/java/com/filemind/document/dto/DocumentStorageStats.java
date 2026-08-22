package com.filemind.document.dto;

public record DocumentStorageStats(
    long logicalSize,
    long actualStoredSize,
    long dedupSavedSize,
    double dedupRatio,
    long totalChunkReferences,
    long uniqueChunkCount,
    long reusedChunkReferences,
    double averageChunkSizeBytes,
    double averageStoredChunkSizeBytes,
    double compressionRatio
) {
}
