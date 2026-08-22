package com.filemind.document.dto;

import java.time.Instant;
import java.util.List;

public record DocumentListItemResponse(
    String id,
    String name,
    String mediaType,
    String ownerName,
    String permission,
    boolean canEdit,
    int currentVersionNumber,
    int totalVersions,
    long logicalSize,
    long actualStoredSize,
    long dedupSavedSize,
    double dedupRatio,
    long totalChunkReferences,
    long uniqueChunkCount,
    long reusedChunkReferences,
    Instant updatedAt,
    String summary,
    List<String> tags,
    String searchSnippet,
    String processingStatus,
    String processingMessage,
    boolean readyForViewing
) {
}
