package com.filemind.document.repository;

import com.filemind.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, String> {

    List<Document> findByOwnerIdAndDeletedFalseOrderByUpdatedAtDesc(String ownerId);

    Optional<Document> findByIdAndDeletedFalse(String id);

    @Query("""
        select distinct d from Document d
        left join DocumentAccess access on access.document = d and access.user.id = :userId
        where d.deleted = false and (d.owner.id = :userId or access.id is not null)
        order by d.updatedAt desc
        """)
    List<Document> findAccessibleDocuments(@Param("userId") String userId);

    @Modifying(flushAutomatically = true)
    @Query("""
        update Document d
        set d.lastViewedAt = :lastViewedAt,
            d.lastViewedByName = :lastViewedByName,
            d.lastViewedByEmail = :lastViewedByEmail
        where d.id = :documentId
        """)
    void updateViewMetadata(@Param("documentId") String documentId,
                            @Param("lastViewedAt") Instant lastViewedAt,
                            @Param("lastViewedByName") String lastViewedByName,
                            @Param("lastViewedByEmail") String lastViewedByEmail);
}
