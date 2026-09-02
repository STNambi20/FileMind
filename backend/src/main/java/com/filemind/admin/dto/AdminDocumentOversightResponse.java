package com.filemind.admin.dto;

import java.time.Instant;

public record AdminDocumentOversightResponse(
    String id,
    String name,
    String ownerName,
    String ownerEmail,
    String processingStatus,
    long versionCount,
    long logicalSizeBytes,
    Instant updatedAt
) {
}
