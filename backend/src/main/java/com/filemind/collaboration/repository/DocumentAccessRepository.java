package com.filemind.collaboration.repository;

import com.filemind.collaboration.entity.DocumentAccess;
import com.filemind.common.persistence.DynamoMetadataStore;
import com.filemind.common.persistence.DynamoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DocumentAccessRepository extends DynamoRepository<DocumentAccess> {
    public DocumentAccessRepository(DynamoMetadataStore store) { super(store, DocumentAccess.class); }
    @Override protected String id(DocumentAccess access) { return access.getId(); }

    public Optional<DocumentAccess> findByDocumentIdAndUserId(String documentId, String userId) { return findAll().stream().filter(a -> documentId.equals(a.getDocument().getId()) && userId.equals(a.getUser().getId())).findFirst(); }

    public List<DocumentAccess> findByDocumentIdOrderByCreatedAtAsc(String documentId) { return findAll().stream().filter(a -> documentId.equals(a.getDocument().getId())).sorted(java.util.Comparator.comparing(DocumentAccess::getCreatedAt)).toList(); }

    public void deleteByDocumentId(String documentId) { findByDocumentIdOrderByCreatedAtAsc(documentId).forEach(this::delete); }
}
