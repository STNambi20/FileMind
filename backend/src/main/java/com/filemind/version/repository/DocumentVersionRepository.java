package com.filemind.version.repository;

import com.filemind.version.entity.DocumentVersion;
import com.filemind.common.persistence.DynamoMetadataStore;
import com.filemind.common.persistence.DynamoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DocumentVersionRepository extends DynamoRepository<DocumentVersion> {
    public DocumentVersionRepository(DynamoMetadataStore store) { super(store, DocumentVersion.class); }
    @Override protected String id(DocumentVersion version) { return version.getId(); }

    public List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(String documentId) { return byDocument(documentId); }

    public List<DocumentVersion> findByDocumentIdInOrderByDocumentIdAscVersionNumberDesc(List<String> ids) { return findAll().stream().filter(v -> ids.contains(v.getDocument().getId())).sorted(java.util.Comparator.comparing((DocumentVersion v) -> v.getDocument().getId()).thenComparing(DocumentVersion::getVersionNumber).reversed()).toList(); }

    public Optional<DocumentVersion> findFirstByDocumentIdOrderByVersionNumberDesc(String documentId) { return byDocument(documentId).stream().findFirst(); }

    public Optional<DocumentVersion> findFirstByDocumentIdAndCurrentVersionTrue(String documentId) { return byDocument(documentId).stream().filter(DocumentVersion::isCurrentVersion).findFirst(); }

    public long countByDocumentId(String documentId) { return byDocument(documentId).size(); }

    public List<String> findAllManifestPaths() { return findAll().stream().map(DocumentVersion::getManifestPath).filter(java.util.Objects::nonNull).toList(); }

    public long totalLogicalBytes() { return findAll().stream().mapToLong(DocumentVersion::getLogicalSize).sum(); }
    private List<DocumentVersion> byDocument(String documentId) { return findAll().stream().filter(v -> v.getDocument().getId().equals(documentId)).sorted(java.util.Comparator.comparing(DocumentVersion::getVersionNumber).reversed()).toList(); }
}
