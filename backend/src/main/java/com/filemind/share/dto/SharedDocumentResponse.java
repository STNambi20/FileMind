package com.filemind.share.dto;

import java.time.Instant;
import java.util.List;

public record SharedDocumentResponse(
    String id,
    String name,
    String ownerName,
    Instant updatedAt,
    String mediaType,
    int currentVersionNumber,
    long logicalSize,
    String summary,
    List<String> tags,
    String extractedTextPreview
) {
}
