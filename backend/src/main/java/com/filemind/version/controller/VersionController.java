package com.filemind.version.controller;

import com.filemind.common.response.ApiResponse;
import com.filemind.common.security.AuthenticatedUserService;
import com.filemind.document.service.DocumentService;
import com.filemind.user.entity.User;
import com.filemind.version.dto.RestoreVersionResponse;
import com.filemind.version.dto.VersionSummaryResponse;
import com.filemind.version.entity.DocumentVersion;
import com.filemind.version.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;
    private final AuthenticatedUserService authenticatedUserService;
    private final DocumentService documentService;

    @GetMapping("/documents/{documentId}/versions")
    public ApiResponse<List<VersionSummaryResponse>> list(@PathVariable String documentId) {
        User user = authenticatedUserService.getCurrentUser();
        documentService.getReadableDocument(user, documentId);
        return ApiResponse.success("Versions ready", versionService.listVersions(documentId));
    }

    @GetMapping("/versions/{versionId}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String versionId) {
        User user = authenticatedUserService.getCurrentUser();
        DocumentVersion version = versionService.getVersionEntity(versionId);
        documentService.getReadableDocument(user, version.getDocument().getId());
        byte[] content = versionService.download(user, versionId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + version.getFileName() + "\"")
            .contentType(MediaType.parseMediaType(version.getMediaType()))
            .body(new ByteArrayResource(content));
    }

    @PostMapping("/versions/{versionId}/restore")
    public ApiResponse<RestoreVersionResponse> restore(@PathVariable String versionId) {
        User user = authenticatedUserService.getCurrentUser();
        DocumentVersion version = versionService.getVersionEntity(versionId);
        documentService.getEditableDocument(user, version.getDocument().getId());
        return ApiResponse.success(
            "Version restored",
            versionService.restoreVersion(user, versionId)
        );
    }
}
