package com.filemind.chunk.dto;

public record StorageCleanupResponse(
    long removedChunkObjects,
    long removedStoredBytes,
    long correctedReferenceCounts
) {
}
