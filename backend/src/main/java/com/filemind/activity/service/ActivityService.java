package com.filemind.activity.service;

import com.filemind.activity.dto.ActivityItemResponse;
import com.filemind.activity.entity.ActivityAction;
import com.filemind.activity.entity.ActivityEvent;
import com.filemind.activity.repository.ActivityEventRepository;
import com.filemind.document.entity.Document;
import com.filemind.user.entity.User;
import com.filemind.version.entity.DocumentVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityEventRepository activityEventRepository;

    public void log(ActivityAction action, User actor, Document document, String details) {
        log(action, actor, document, null, details);
    }

    public void log(ActivityAction action, User actor, Document document, DocumentVersion version, String details) {
        ActivityEvent event = new ActivityEvent();
        event.setAction(action);
        event.setActorName(actor.getFullName());
        event.setActorEmail(actor.getEmail());
        event.setDocumentId(document != null ? document.getId() : null);
        event.setDocumentName(document != null ? document.getName() : null);
        event.setVersionId(version != null ? version.getId() : null);
        event.setVersionNumber(version != null ? version.getVersionNumber() : null);
        event.setDetails(details);
        activityEventRepository.save(event);
    }

    public List<ActivityItemResponse> recentActivity() {
        return activityEventRepository.findTop20ByOrderByCreatedAtDesc()
            .stream()
            .map(ActivityItemResponse::from)
            .toList();
    }

    public List<ActivityItemResponse> recentWorkspaceActivity(User user, Collection<String> documentIds) {
        List<ActivityEvent> events = documentIds == null || documentIds.isEmpty()
            ? activityEventRepository.findTop20ByActorEmailOrderByCreatedAtDesc(user.getEmail())
            : activityEventRepository.findRecentWorkspaceActivity(user.getEmail(), documentIds);

        return events.stream()
            .limit(20)
            .map(ActivityItemResponse::from)
            .toList();
    }

    public List<ActivityItemResponse> recentDocumentActivity(String documentId) {
        return activityEventRepository.findTop20ByDocumentIdOrderByCreatedAtDesc(documentId)
            .stream()
            .map(ActivityItemResponse::from)
            .toList();
    }
}
