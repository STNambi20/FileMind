package com.filemind.share.controller;

import com.filemind.common.response.ApiResponse;
import com.filemind.common.security.AuthenticatedUserService;
import com.filemind.share.dto.EnableShareLinkRequest;
import com.filemind.share.dto.ShareLinkResponse;
import com.filemind.share.dto.SharedDocumentResponse;
import com.filemind.share.service.PublicShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PublicShareController {

    private final PublicShareService publicShareService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/api/documents/{documentId}/share-link")
    public ApiResponse<ShareLinkResponse> getShareLink(@PathVariable String documentId) {
        return ApiResponse.success(
            "Share link ready",
            publicShareService.getShareLink(authenticatedUserService.getCurrentUser(), documentId)
        );
    }

    @PostMapping("/api/documents/{documentId}/share-link")
    public ApiResponse<ShareLinkResponse> enable(@PathVariable String documentId,
                                                 @Valid @RequestBody(required = false) EnableShareLinkRequest request) {
        return ApiResponse.success(
            "Share link enabled",
            publicShareService.enable(
                authenticatedUserService.getCurrentUser(),
                documentId,
                request == null ? null : request.expiresInDays()
            )
        );
    }

    @DeleteMapping("/api/documents/{documentId}/share-link")
    public ApiResponse<Void> disable(@PathVariable String documentId) {
        publicShareService.disable(authenticatedUserService.getCurrentUser(), documentId);
        return ApiResponse.success("Share link disabled", null);
    }

    @GetMapping("/api/public/share/{token}")
    public ApiResponse<SharedDocumentResponse> sharedDocument(@PathVariable String token) {
        return ApiResponse.success(
            "Shared document ready",
            publicShareService.getSharedDocument(token)
        );
    }

    @GetMapping("/api/public/share/{token}/download")
    public ResponseEntity<ByteArrayResource> downloadSharedDocument(@PathVariable String token) {
        return publicShareService.downloadSharedDocument(token);
    }
}
