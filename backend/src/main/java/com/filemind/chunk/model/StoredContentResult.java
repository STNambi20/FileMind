package com.filemind.chunk.model;

import java.util.List;

public record StoredContentResult(
    String manifestPath,
    String contentHash,
    long logicalSize,
    long actualStoredSize,
    long dedupSavedSize,
    int totalChunkCount,
    int newChunkCount,
    int reusedChunkCount,
    List<String> newChunkHashes
) {
}
