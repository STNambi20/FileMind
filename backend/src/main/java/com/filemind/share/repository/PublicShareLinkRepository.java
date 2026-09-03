package com.filemind.share.repository;

import com.filemind.share.entity.PublicShareLink;
import com.filemind.common.persistence.DynamoMetadataStore;
import com.filemind.common.persistence.DynamoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PublicShareLinkRepository extends DynamoRepository<PublicShareLink> {
    public PublicShareLinkRepository(DynamoMetadataStore store) { super(store, PublicShareLink.class); }
    @Override protected String id(PublicShareLink link) { return link.getId(); }

    public Optional<PublicShareLink> findByDocumentId(String documentId) { return findAll().stream().filter(l -> documentId.equals(l.getDocument().getId())).findFirst(); }

    public Optional<PublicShareLink> findByTokenAndEnabledTrue(String token) { return findAll().stream().filter(l -> l.isEnabled() && token.equals(l.getToken())).findFirst(); }

    public void deleteByDocumentId(String documentId) { findByDocumentId(documentId).ifPresent(this::delete); }
}
