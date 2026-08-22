package com.filemind.share.service;

import com.filemind.activity.entity.ActivityAction;
import com.filemind.activity.service.ActivityService;
import com.filemind.collaboration.service.DocumentAccessService;
import com.filemind.common.util.JsonListCodec;
import com.filemind.common.exception.ResourceNotFoundException;
import com.filemind.config.properties.ShareProperties;
import com.filemind.document.entity.Document;
import com.filemind.share.dto.ShareLinkResponse;
import com.filemind.share.dto.SharedDocumentResponse;
import com.filemind.share.entity.PublicShareLink;
import com.filemind.share.repository.PublicShareLinkRepository;
import com.filemind.user.entity.User;
import com.filemind.version.entity.DocumentVersion;
import com.filemind.version.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PublicShareService {

    private final PublicShareLinkRepository publicShareLinkRepository;
    private final DocumentAccessService documentAccessService;
    private final VersionService versionService;
    private final ActivityService activityService;
    private final ShareProperties shareProperties;
    private final JsonListCodec jsonListCodec;

    @Transactional
    public ShareLinkResponse enable(User actor, String documentId) {
        return enable(actor, documentId, null);
    }

    @Transactional
    public ShareLinkResponse enable(User actor, String documentId, Integer expiresInDays) {
        Document document = documentAccessService.requireOwnedDocument(actor, documentId);
        PublicShareLink shareLink = publicShareLinkRepository.findByDocumentId(documentId).orElseGet(PublicShareLink::new);
        shareLink.setDocument(document);
        shareLink.setEnabled(true);
        shareLink.setExpiresAt(resolveExpiresAt(expiresInDays));
        shareLink = publicShareLinkRepository.save(shareLink);

        activityService.log(
            ActivityAction.SHARE_LINK_ENABLED,
            actor,
            document,
            expiresInDays == null
                ? "Enabled public share link"
                : "Enabled public share link for " + expiresInDays + " day" + (expiresInDays == 1 ? "" : "s")
        );
        return toResponse(shareLink);
    }

    @Transactional
    public void disable(User actor, String documentId) {
        Document document = documentAccessService.requireOwnedDocument(actor, documentId);
        PublicShareLink shareLink = publicShareLinkRepository.findByDocumentId(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("No public share link exists for this document"));
        shareLink.setEnabled(false);
        publicShareLinkRepository.save(shareLink);
        activityService.log(ActivityAction.SHARE_LINK_DISABLED, actor, document, "Disabled public share link");
    }

    @Transactional(readOnly = true)
    public ShareLinkResponse getShareLink(User actor, String documentId) {
        Document document = documentAccessService.requireOwnedDocument(actor, documentId);
        return publicShareLinkRepository.findByDocumentId(document.getId())
            .map(this::toResponse)
            .orElse(new ShareLinkResponse(false, null, null, null, false));
    }

    @Transactional(readOnly = true)
    public String getEnabledShareUrl(Document document) {
        return publicShareLinkRepository.findByDocumentId(document.getId())
            .filter(PublicShareLink::isEnabled)
            .map(this::toResponse)
            .map(ShareLinkResponse::url)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public SharedDocumentResponse getSharedDocument(String token) {
        PublicShareLink shareLink = getEnabledShare(token);
        DocumentVersion currentVersion = versionService.getCurrentVersion(shareLink.getDocument().getId());
        return new SharedDocumentResponse(
            shareLink.getDocument().getId(),
            shareLink.getDocument().getName(),
            shareLink.getDocument().getOwner().getFullName(),
            shareLink.getDocument().getUpdatedAt(),
            currentVersion.getMediaType(),
            currentVersion.getVersionNumber(),
            currentVersion.getLogicalSize(),
            currentVersion.getSummary(),
            jsonListCodec.read(currentVersion.getTagsJson()),
            currentVersion.getExtractedText() == null ? null : currentVersion.getExtractedText().substring(0, Math.min(600, currentVersion.getExtractedText().length()))
        );
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ByteArrayResource> downloadSharedDocument(String token) {
        PublicShareLink shareLink = getEnabledShare(token);
        DocumentVersion currentVersion = versionService.getCurrentVersion(shareLink.getDocument().getId());
        byte[] content = versionService.download(currentVersion.getId());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + currentVersion.getFileName() + "\"")
            .contentType(MediaType.parseMediaType(currentVersion.getMediaType()))
            .body(new ByteArrayResource(content));
    }

    @Transactional
    public void deleteForDocument(String documentId) {
        publicShareLinkRepository.deleteByDocumentId(documentId);
    }

    private PublicShareLink getEnabledShare(String token) {
        return publicShareLinkRepository.findByTokenAndEnabledTrue(token)
            .filter(this::isAvailable)
            .orElseThrow(() -> new ResourceNotFoundException("Shared document link is not available"));
    }

    private ShareLinkResponse toResponse(PublicShareLink shareLink) {
        boolean expired = isExpired(shareLink);
        boolean enabled = shareLink.isEnabled() && !expired;
        if (!enabled) {
            return new ShareLinkResponse(false, shareLink.getToken(), null, shareLink.getExpiresAt(), expired);
        }

        String baseUrl = shareProperties.getBaseUrl() == null ? "" : shareProperties.getBaseUrl().replaceAll("/+$", "");
        return new ShareLinkResponse(true, shareLink.getToken(), baseUrl + "/shared/" + shareLink.getToken(), shareLink.getExpiresAt(), false);
    }

    private Instant resolveExpiresAt(Integer expiresInDays) {
        if (expiresInDays == null) {
            return null;
        }
        return Instant.now().plusSeconds(expiresInDays.longValue() * 24L * 60L * 60L);
    }

    private boolean isAvailable(PublicShareLink shareLink) {
        return shareLink.isEnabled() && !isExpired(shareLink);
    }

    private boolean isExpired(PublicShareLink shareLink) {
        return shareLink.getExpiresAt() != null && shareLink.getExpiresAt().isBefore(Instant.now());
    }

}
