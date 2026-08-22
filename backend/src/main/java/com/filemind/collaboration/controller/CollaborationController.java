package com.filemind.collaboration.controller;

import com.filemind.collaboration.dto.CollaboratorResponse;
import com.filemind.collaboration.dto.ShareDocumentRequest;
import com.filemind.collaboration.dto.UpdateCollaboratorPermissionRequest;
import com.filemind.collaboration.service.DocumentAccessService;
import com.filemind.common.response.ApiResponse;
import com.filemind.common.security.AuthenticatedUserService;
import com.filemind.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{documentId}/collaborators")
@RequiredArgsConstructor
public class CollaborationController {

    private final DocumentAccessService documentAccessService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    public ApiResponse<List<CollaboratorResponse>> list(@PathVariable String documentId) {
        User user = authenticatedUserService.getCurrentUser();
        return ApiResponse.success(
            "Collaborators ready",
            documentAccessService.listCollaborators(documentAccessService.requireOwnedDocument(user, documentId))
        );
    }

    @PostMapping
    public ApiResponse<CollaboratorResponse> share(@PathVariable String documentId,
                                                   @Valid @RequestBody ShareDocumentRequest request) {
        return ApiResponse.success(
            "Access shared",
            documentAccessService.share(authenticatedUserService.getCurrentUser(), documentId, request)
        );
    }

    @PatchMapping("/{collaboratorId}")
    public ApiResponse<CollaboratorResponse> update(@PathVariable String documentId,
                                                    @PathVariable String collaboratorId,
                                                    @Valid @RequestBody UpdateCollaboratorPermissionRequest request) {
        return ApiResponse.success(
            "Access updated",
            documentAccessService.updatePermission(authenticatedUserService.getCurrentUser(), documentId, collaboratorId, request)
        );
    }

    @DeleteMapping("/{collaboratorId}")
    public ApiResponse<Void> revoke(@PathVariable String documentId, @PathVariable String collaboratorId) {
        documentAccessService.revoke(authenticatedUserService.getCurrentUser(), documentId, collaboratorId);
        return ApiResponse.success("Access removed", null);
    }
}
