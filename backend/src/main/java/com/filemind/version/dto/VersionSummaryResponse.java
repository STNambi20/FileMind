package com.filemind.version.dto;

import java.time.Instant;
import java.util.List;

public record VersionSummaryResponse(
    String id,
    int versionNumber,
    String fileName,
    String mediaType,
    String origin,
    long logicalSize,
    long actualStoredSize,
    long dedupSavedSize,
    double dedupRatio,
    int totalChunkCount,
    int newChunkCount,
    int reusedChunkCount,
    Instant createdAt,
    String createdBy,
    boolean currentVersion,
    String note,
    String summary,
    String titleSuggestion,
    List<String> keyInsights,
    List<String> bulletHighlights,
    String readabilityNote,
    boolean ocrApplied,
    List<String> tags,
    String processingStatus,
    String processingError,
    boolean readyForUse
) {
}
