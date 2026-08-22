package com.filemind.document.dto;

import com.filemind.activity.dto.ActivityItemResponse;
import com.filemind.comment.dto.DocumentCommentResponse;
import com.filemind.collaboration.dto.CollaboratorResponse;
import com.filemind.version.dto.VersionSummaryResponse;

import java.time.Instant;
import java.util.List;

public record DocumentDetailsResponse(
    String id,
    String name,
    String ownerName,
    String ownerEmail,
    String permission,
    boolean canEdit,
    boolean canManageAccess,
    Instant createdAt,
    Instant updatedAt,
    Instant lastViewedAt,
    String lastViewedByName,
    String lastViewedByEmail,
    DocumentStorageStats storage,
    int totalVersions,
    VersionSummaryResponse currentVersion,
    VersionSummaryResponse latestVersion,
    String processingStatus,
    String processingMessage,
    boolean readyForViewing,
    String summary,
    String titleSuggestion,
    List<String> keyInsights,
    List<String> bulletHighlights,
    String readabilityNote,
    List<String> tags,
    String extractedTextPreview,
    DocumentInsightMetadataResponse smartMetadata,
    List<RelatedDocumentResponse> relatedDocuments,
    String publicShareUrl,
    Instant publicShareExpiresAt,
    boolean publicShareExpired,
    List<CollaboratorResponse> collaborators,
    List<ActivityItemResponse> recentActivity,
    List<DocumentCommentResponse> comments
) {
}
