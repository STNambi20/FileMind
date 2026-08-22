package com.filemind.activity.repository;

import com.filemind.activity.entity.ActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, String> {

    List<ActivityEvent> findTop20ByOrderByCreatedAtDesc();

    List<ActivityEvent> findTop20ByDocumentIdOrderByCreatedAtDesc(String documentId);

    List<ActivityEvent> findTop20ByActorEmailOrderByCreatedAtDesc(String actorEmail);

    @Query("""
        select e from ActivityEvent e
        where e.actorEmail = :actorEmail or e.documentId in :documentIds
        order by e.createdAt desc
        """)
    List<ActivityEvent> findRecentWorkspaceActivity(@Param("actorEmail") String actorEmail,
                                                    @Param("documentIds") Collection<String> documentIds);
}
