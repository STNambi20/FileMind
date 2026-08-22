package com.filemind.document.dto;

public record DocumentProcessingStatusResponse(
    String documentId,
    String status,
    String message,
    boolean readyForViewing,
    String currentVersionId,
    Integer currentVersionNumber,
    int totalVersions
) {
}
