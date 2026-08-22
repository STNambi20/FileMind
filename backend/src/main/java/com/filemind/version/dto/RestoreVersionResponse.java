package com.filemind.version.dto;

public record RestoreVersionResponse(
    String versionId,
    int versionNumber,
    String message
) {
}

