package com.filemind.document.dto;

public record UploadDocumentResponse(
    String documentId,
    String versionId,
    boolean versionCreated,
    int versionNumber,
    String status,
    boolean readyForViewing,
    String message
) {
}
