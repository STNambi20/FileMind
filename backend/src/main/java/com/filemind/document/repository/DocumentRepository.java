package com.filemind.document.repository;

import com.filemind.document.entity.Document;
import com.filemind.collaboration.entity.DocumentAccess;
import com.filemind.common.persistence.DynamoMetadataStore;
import com.filemind.common.persistence.DynamoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class DocumentRepository extends DynamoRepository<Document> {
    public DocumentRepository(DynamoMetadataStore store) { super(store, Document.class); }
    @Override protected String id(Document document) { return document.getId(); }

    public List<Document> findByOwnerIdAndDeletedFalseOrderByUpdatedAtDesc(String ownerId) { return active().stream().filter(d -> ownerId.equals(d.getOwner().getId())).sorted(updated()).toList(); }

    public Optional<Document> findByIdAndDeletedFalse(String id) { return findById(id).filter(d -> !d.isDeleted()); }

    public List<Document> findAccessibleDocuments(String userId) {
        var shared = store.findAll(DocumentAccess.class).stream().filter(a -> userId.equals(a.getUser().getId())).map(a -> a.getDocument().getId()).collect(java.util.stream.Collectors.toSet());
        return active().stream().filter(d -> userId.equals(d.getOwner().getId()) || shared.contains(d.getId())).sorted(updated()).toList();
    }

    public void updateViewMetadata(String id, Instant viewedAt, String name, String email) { findById(id).ifPresent(d -> { d.setLastViewedAt(viewedAt); d.setLastViewedByName(name); d.setLastViewedByEmail(email); save(d); }); }
    private List<Document> active() { return findAll().stream().filter(d -> !d.isDeleted()).toList(); }
    private java.util.Comparator<Document> updated() { return java.util.Comparator.comparing(Document::getUpdatedAt).reversed(); }
}
