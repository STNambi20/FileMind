package com.filemind.admin.dto;

public record AdminSystemHealthResponse(
    String backendStatus,
    String databaseStatus,
    String aiServiceStatus,
    long failedDocuments,
    long processingDocuments,
    long activeUsers
) {
}
