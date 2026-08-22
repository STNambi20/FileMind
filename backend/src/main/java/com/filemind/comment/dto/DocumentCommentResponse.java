package com.filemind.comment.dto;

import com.filemind.comment.entity.DocumentComment;

import java.time.Instant;

public record DocumentCommentResponse(
    String id,
    String content,
    String authorName,
    String authorEmail,
    Instant createdAt,
    String versionId,
    Integer versionNumber
) {

    public static DocumentCommentResponse from(DocumentComment comment) {
        return new DocumentCommentResponse(
            comment.getId(),
            comment.getContent(),
            comment.getAuthorName(),
            comment.getAuthorEmail(),
            comment.getCreatedAt(),
            comment.getVersion() != null ? comment.getVersion().getId() : null,
            comment.getVersion() != null ? comment.getVersion().getVersionNumber() : null
        );
    }
}
