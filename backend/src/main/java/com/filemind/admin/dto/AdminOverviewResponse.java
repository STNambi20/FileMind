package com.filemind.admin.dto;

import java.util.List;

public record AdminOverviewResponse(
    long totalUsers,
    long adminUsers,
    long totalDocuments,
    long failedDocuments,
    long processingDocuments,
    AdminSystemHealthResponse systemHealth,
    List<AdminUserResponse> users,
    List<AdminDocumentOversightResponse> documents
) {
}
