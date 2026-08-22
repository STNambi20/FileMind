package com.filemind.activity.dto;

import com.filemind.activity.entity.ActivityEvent;

import java.time.Instant;

public record ActivityItemResponse(
    String id,
    String action,
    String actorName,
    String actorEmail,
    String documentId,
    String documentName,
    String versionId,
    Integer versionNumber,
    String details,
    Instant createdAt
) {
    public static ActivityItemResponse from(ActivityEvent event) {
        return new ActivityItemResponse(
            event.getId(),
            event.getAction().label(),
            event.getActorName(),
            event.getActorEmail(),
            event.getDocumentId(),
            event.getDocumentName(),
            event.getVersionId(),
            event.getVersionNumber(),
            event.getDetails(),
            event.getCreatedAt()
        );
    }
}
