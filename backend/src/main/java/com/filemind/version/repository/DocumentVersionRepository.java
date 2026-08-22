package com.filemind.version.repository;

import com.filemind.version.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, String> {

    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(String documentId);

    List<DocumentVersion> findByDocumentIdInOrderByDocumentIdAscVersionNumberDesc(List<String> documentIds);

    Optional<DocumentVersion> findFirstByDocumentIdOrderByVersionNumberDesc(String documentId);

    Optional<DocumentVersion> findFirstByDocumentIdAndCurrentVersionTrue(String documentId);

    long countByDocumentId(String documentId);

    @Query("select v.manifestPath from DocumentVersion v where v.manifestPath is not null")
    List<String> findAllManifestPaths();

    @Query("select coalesce(sum(v.logicalSize), 0) from DocumentVersion v")
    long totalLogicalBytes();
}
