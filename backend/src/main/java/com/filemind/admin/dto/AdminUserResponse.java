package com.filemind.admin.dto;

import com.filemind.user.entity.User;

import java.time.Instant;

public record AdminUserResponse(
    String id,
    String fullName,
    String email,
    String role,
    Instant createdAt,
    long documentCount
) {
    public static AdminUserResponse from(User user, long documentCount) {
        return new AdminUserResponse(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getRole().name(),
            user.getCreatedAt(),
            documentCount
        );
    }
}
