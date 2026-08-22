package com.filemind.dashboard.dto;

import java.time.Instant;

public record RecentAccessDocumentResponse(
    String id,
    String name,
    int currentVersionNumber,
    String mediaType,
    Instant lastViewedAt,
    String lastViewedByName,
    String lastViewedByEmail
) {
}
