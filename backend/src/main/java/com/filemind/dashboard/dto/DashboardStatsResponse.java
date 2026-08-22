package com.filemind.dashboard.dto;

import com.filemind.activity.dto.ActivityItemResponse;

import java.util.List;

public record DashboardStatsResponse(
    long totalFiles,
    long totalVersions,
    long logicalStorageBytes,
    long actualStoredBytes,
    long savedBytes,
    double dedupRatio,
    long totalChunkReferences,
    long uniqueChunkCount,
    long reusedChunkReferences,
    double averageChunkSizeBytes,
    double averageStoredChunkSizeBytes,
    double compressionRatio,
    long aiProcessedCount,
    List<DashboardDocumentResponse> recentDocuments,
    List<RecentAccessDocumentResponse> recentAccessDocuments,
    List<ActivityItemResponse> recentActivity
) {
}
