package com.filemind.chunk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StoredManifest(
    String contentHash,
    String fileName,
    String mediaType,
    long logicalSize,
    ManifestStorageMode storageMode,
    List<ManifestChunk> chunks,
    List<ManifestPart> parts
) {

    public StoredManifest {
        storageMode = storageMode == null ? ManifestStorageMode.BINARY_CHUNKS : storageMode;
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    public StoredManifest(String contentHash,
                          String fileName,
                          String mediaType,
                          long logicalSize,
                          List<ManifestChunk> chunks) {
        this(contentHash, fileName, mediaType, logicalSize, ManifestStorageMode.BINARY_CHUNKS, chunks, List.of());
    }

    public StoredManifest(String contentHash,
                          String fileName,
                          String mediaType,
                          long logicalSize,
                          ManifestStorageMode storageMode,
                          List<ManifestPart> parts) {
        this(contentHash, fileName, mediaType, logicalSize, storageMode, List.of(), parts);
    }

    public List<ManifestChunk> allChunks() {
        if (!parts.isEmpty()) {
            return parts.stream()
                .flatMap(part -> part.chunks().stream())
                .toList();
        }
        return chunks;
    }
}
