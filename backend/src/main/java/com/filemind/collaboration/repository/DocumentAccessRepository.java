package com.filemind.collaboration.repository;

import com.filemind.collaboration.entity.DocumentAccess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentAccessRepository extends JpaRepository<DocumentAccess, String> {

    Optional<DocumentAccess> findByDocumentIdAndUserId(String documentId, String userId);

    List<DocumentAccess> findByDocumentIdOrderByCreatedAtAsc(String documentId);

    void deleteByDocumentId(String documentId);
}
