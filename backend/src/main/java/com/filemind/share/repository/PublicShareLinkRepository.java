package com.filemind.share.repository;

import com.filemind.share.entity.PublicShareLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublicShareLinkRepository extends JpaRepository<PublicShareLink, String> {

    Optional<PublicShareLink> findByDocumentId(String documentId);

    Optional<PublicShareLink> findByTokenAndEnabledTrue(String token);

    void deleteByDocumentId(String documentId);
}
