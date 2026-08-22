package com.filemind.comment.controller;

import com.filemind.comment.dto.CreateDocumentCommentRequest;
import com.filemind.comment.dto.DocumentCommentResponse;
import com.filemind.comment.service.DocumentCommentService;
import com.filemind.common.response.ApiResponse;
import com.filemind.common.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{documentId}/comments")
@RequiredArgsConstructor
public class DocumentCommentController {

    private final DocumentCommentService documentCommentService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    public ApiResponse<List<DocumentCommentResponse>> list(@PathVariable String documentId) {
        return ApiResponse.success(
            "Review notes ready",
            documentCommentService.list(authenticatedUserService.getCurrentUser(), documentId)
        );
    }

    @PostMapping
    public ApiResponse<DocumentCommentResponse> add(@PathVariable String documentId,
                                                    @Valid @RequestBody CreateDocumentCommentRequest request) {
        return ApiResponse.success(
            "Review note added",
            documentCommentService.add(authenticatedUserService.getCurrentUser(), documentId, request)
        );
    }
}
