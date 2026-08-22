package com.filemind.document.dto;

import com.filemind.activity.dto.ActivityItemResponse;
import com.filemind.collaboration.dto.CollaboratorResponse;
import com.filemind.version.dto.VersionSummaryResponse;

import java.time.Instant;
import java.util.List;

public record DocumentViewerResponse(
    String documentId,
    String documentName,
    String ownerName,
    String ownerEmail,
    String permission,
    boolean canEdit,
    boolean canManageAccess,
    Instant lastViewedAt,
    String lastViewedByName,
    String lastViewedByEmail,
    DocumentStorageStats storage,
    int totalVersions,
    VersionSummaryResponse version,
    String previewMode,
    String previewText,
    boolean previewTruncated,
    List<CollaboratorResponse> collaborators,
    List<ActivityItemResponse> recentActivity
) {
}
