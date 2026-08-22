package com.filemind.chunk.model;

public record ManifestChunk(
    String hash,
    int order,
    int size
) {
}

