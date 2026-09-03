package com.filemind.comment.repository;

import com.filemind.comment.entity.DocumentComment;
import com.filemind.common.persistence.DynamoMetadataStore;
import com.filemind.common.persistence.DynamoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DocumentCommentRepository extends DynamoRepository<DocumentComment> {
    public DocumentCommentRepository(DynamoMetadataStore store) { super(store, DocumentComment.class); }
    @Override protected String id(DocumentComment comment) { return comment.getId(); }

    public List<DocumentComment> findByDocumentIdOrderByCreatedAtDesc(String documentId) { return findAll().stream().filter(c -> documentId.equals(c.getDocument().getId())).sorted(java.util.Comparator.comparing(DocumentComment::getCreatedAt).reversed()).toList(); }
}
