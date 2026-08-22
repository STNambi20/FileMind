package com.filemind.ai.controller;

import com.filemind.ai.dto.AiRewriteRequest;
import com.filemind.ai.dto.AiRewriteResponse;
import com.filemind.ai.dto.AiQuestionRequest;
import com.filemind.ai.dto.AiQuestionResponse;
import com.filemind.ai.dto.AiSummaryResponse;
import com.filemind.ai.dto.AiTagsResponse;
import com.filemind.ai.service.AiService;
import com.filemind.common.response.ApiResponse;
import com.filemind.common.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/{documentId}/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final AuthenticatedUserService authenticatedUserService;

    @PostMapping("/summary")
    public ApiResponse<AiSummaryResponse> summarize(@PathVariable String documentId,
                                                    @RequestParam(defaultValue = "3") int maxSentences) {
        return ApiResponse.success(
            "Summary ready",
            aiService.summarize(authenticatedUserService.getCurrentUser(), documentId, maxSentences)
        );
    }

    @PostMapping("/tags")
    public ApiResponse<AiTagsResponse> tags(@PathVariable String documentId) {
        return ApiResponse.success(
            "Tags ready",
            aiService.tags(authenticatedUserService.getCurrentUser(), documentId)
        );
    }

    @PostMapping("/rewrite")
    public ApiResponse<AiRewriteResponse> rewrite(@PathVariable String documentId,
                                                  @Valid @RequestBody AiRewriteRequest request) {
        return ApiResponse.success(
            "Rewrite ready",
            aiService.rewrite(authenticatedUserService.getCurrentUser(), documentId, request)
        );
    }

    @PostMapping("/ask")
    public ApiResponse<AiQuestionResponse> ask(@PathVariable String documentId,
                                               @Valid @RequestBody AiQuestionRequest request) {
        return ApiResponse.success(
            "Answer ready",
            aiService.ask(authenticatedUserService.getCurrentUser(), documentId, request)
        );
    }
}
