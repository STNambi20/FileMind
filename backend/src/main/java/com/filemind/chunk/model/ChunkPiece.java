package com.filemind.chunk.model;

public record ChunkPiece(
    int order,
    byte[] bytes,
    String hash,
    int size
) {
}

