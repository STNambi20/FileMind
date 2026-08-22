package com.filemind.chunk.dto;

public record StorageDiagnosticsResponse(
    long logicalSizeBytes,
    long actualStoredBytes,
    long savedBytes,
    double dedupRatio,
    long totalChunks,
    long uniqueChunks,
    long reusedChunks,
    double averageChunkSizeBytes,
    double averageStoredChunkSizeBytes,
    double compressionRatio,
    long uniqueLogicalChunkBytes,
    long totalStoredChunkObjects
) {
}
