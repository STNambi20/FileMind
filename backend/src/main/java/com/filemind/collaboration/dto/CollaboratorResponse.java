package com.filemind.collaboration.dto;

import com.filemind.collaboration.entity.DocumentAccess;

import java.time.Instant;

public record CollaboratorResponse(
    String id,
    String userId,
    String fullName,
    String email,
    String permission,
    Instant sharedAt
) {
    public static CollaboratorResponse from(DocumentAccess access) {
        return new CollaboratorResponse(
            access.getId(),
            access.getUser().getId(),
            access.getUser().getFullName(),
            access.getUser().getEmail(),
            access.getPermission().name(),
            access.getCreatedAt()
        );
    }
}
