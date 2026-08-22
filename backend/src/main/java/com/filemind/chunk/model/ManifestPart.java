package com.filemind.chunk.model;

import java.util.List;

public record ManifestPart(
    String name,
    int order,
    boolean directory,
    boolean chunked,
    long logicalSize,
    Long modifiedTimeEpochMillis,
    String contentHash,
    List<ManifestChunk> chunks
) {
    public ManifestPart {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }
}
