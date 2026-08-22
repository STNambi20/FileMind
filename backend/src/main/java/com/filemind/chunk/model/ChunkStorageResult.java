package com.filemind.chunk.model;

public record ChunkStorageResult(
    String storagePath,
    long storedSizeBytes
) {
}
