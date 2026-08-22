package com.filemind.dashboard.dto;

import java.time.Instant;

public record DashboardDocumentResponse(
    String id,
    String name,
    int currentVersionNumber,
    int totalVersions,
    long logicalSize,
    long actualStoredSize,
    long dedupSavedSize,
    double dedupRatio,
    long uniqueChunkCount,
    long reusedChunkReferences,
    Instant updatedAt
) {
}
