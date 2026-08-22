package com.filemind.user.dto;

import com.filemind.user.entity.User;

import java.time.Instant;

public record CurrentUserResponse(
    String id,
    String fullName,
    String email,
    String avatarUrl,
    String role,
    Instant createdAt
) {
    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getAvatarUrl(),
            user.getRole().name(),
            user.getCreatedAt()
        );
    }
}
