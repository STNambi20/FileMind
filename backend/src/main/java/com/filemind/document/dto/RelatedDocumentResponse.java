package com.filemind.document.dto;

import java.time.Instant;
import java.util.List;

public record RelatedDocumentResponse(
    String id,
    String name,
    String mediaType,
    Instant updatedAt,
    double similarityScore,
    List<String> matchedTerms,
    String summary
) {
}
