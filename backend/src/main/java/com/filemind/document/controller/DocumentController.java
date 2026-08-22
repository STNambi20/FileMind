package com.filemind.document.controller;

import com.filemind.common.response.ApiResponse;
import com.filemind.common.security.AuthenticatedUserService;
import com.filemind.document.dto.DocumentDetailsResponse;
import com.filemind.document.dto.DocumentListItemResponse;
import com.filemind.document.dto.DocumentProcessingStatusResponse;
import com.filemind.document.dto.DocumentViewerResponse;
import com.filemind.document.dto.RenameDocumentRequest;
import com.filemind.document.dto.UploadDocumentResponse;
import com.filemind.document.service.DocumentService;
import com.filemind.document.service.DocumentViewerService;
import com.filemind.version.dto.VersionComparisonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentViewerService documentViewerService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    public ApiResponse<List<DocumentListItemResponse>> list(@RequestParam(required = false) String query,
                                                            @RequestParam(required = false) String type) {
        return ApiResponse.success(
            "Documents ready",
            documentService.listDocuments(authenticatedUserService.getCurrentUser(), query, type)
        );
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadDocumentResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(
            "Document upload accepted",
            documentService.uploadNewDocument(authenticatedUserService.getCurrentUser(), file)
        );
    }

    @PostMapping(value = "/{documentId}/versions/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadDocumentResponse> uploadVersion(@PathVariable String documentId,
                                                             @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(
            "Version upload accepted",
            documentService.uploadNewVersion(authenticatedUserService.getCurrentUser(), documentId, file)
        );
    }

    @GetMapping("/{documentId}/processing-status")
    public ApiResponse<DocumentProcessingStatusResponse> processingStatus(@PathVariable String documentId) {
        return ApiResponse.success(
            "Processing status ready",
            documentService.getProcessingStatus(authenticatedUserService.getCurrentUser(), documentId)
        );
    }

    @PostMapping("/{documentId}/retry-processing")
    public ApiResponse<UploadDocumentResponse> retryProcessing(@PathVariable String documentId) {
        return ApiResponse.success(
            "Processing retry accepted",
            documentService.retryFailedProcessing(authenticatedUserService.getCurrentUser(), documentId)
        );
    }

    @GetMapping("/{documentId}")
    public ApiResponse<DocumentDetailsResponse> details(@PathVariable String documentId) {
        return ApiResponse.success(
            "Document ready",
            documentService.getDetails(authenticatedUserService.getCurrentUser(), documentId)
        );
    }

    @GetMapping("/{documentId}/viewer")
    public ApiResponse<DocumentViewerResponse> viewer(@PathVariable String documentId,
                                                      @RequestParam(required = false) String versionId) {
        return ApiResponse.success(
            "Viewer ready",
            documentViewerService.open(authenticatedUserService.getCurrentUser(), documentId, versionId)
        );
    }

    @GetMapping("/{documentId}/versions/compare")
    public ApiResponse<VersionComparisonResponse> compareVersions(@PathVariable String documentId,
                                                                 @RequestParam String baseVersionId,
                                                                 @RequestParam String targetVersionId) {
        return ApiResponse.success(
            "Comparison ready",
            documentViewerService.compare(authenticatedUserService.getCurrentUser(), documentId, baseVersionId, targetVersionId)
        );
    }

    @PatchMapping("/{documentId}/rename")
    public ApiResponse<DocumentDetailsResponse> rename(@PathVariable String documentId,
                                                       @Valid @RequestBody RenameDocumentRequest request) {
        return ApiResponse.success(
            "Document renamed",
            documentService.rename(authenticatedUserService.getCurrentUser(), documentId, request)
        );
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> delete(@PathVariable String documentId) {
        documentService.delete(authenticatedUserService.getCurrentUser(), documentId);
        return ApiResponse.success("Document deleted", null);
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String documentId) {
        return documentService.downloadCurrent(authenticatedUserService.getCurrentUser(), documentId);
    }
}
