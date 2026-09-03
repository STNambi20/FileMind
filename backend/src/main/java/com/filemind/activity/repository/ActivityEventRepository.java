package com.filemind.activity.repository;

import com.filemind.activity.entity.ActivityEvent;
import com.filemind.common.persistence.DynamoMetadataStore;
import com.filemind.common.persistence.DynamoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class ActivityEventRepository extends DynamoRepository<ActivityEvent> {
    public ActivityEventRepository(DynamoMetadataStore store) { super(store, ActivityEvent.class); }
    @Override protected String id(ActivityEvent event) { return event.getId(); }

    public List<ActivityEvent> findTop20ByOrderByCreatedAtDesc() { return recent(findAll()); }

    public List<ActivityEvent> findTop20ByDocumentIdOrderByCreatedAtDesc(String id) { return recent(findAll().stream().filter(e -> id.equals(e.getDocumentId())).toList()); }

    public List<ActivityEvent> findTop20ByActorEmailOrderByCreatedAtDesc(String email) { return recent(findAll().stream().filter(e -> email.equals(e.getActorEmail())).toList()); }

    public List<ActivityEvent> findRecentWorkspaceActivity(String email, Collection<String> ids) { return recent(findAll().stream().filter(e -> email.equals(e.getActorEmail()) || ids.contains(e.getDocumentId())).toList()); }
    private List<ActivityEvent> recent(List<ActivityEvent> events) { return events.stream().sorted(java.util.Comparator.comparing(ActivityEvent::getCreatedAt).reversed()).limit(20).toList(); }
}
