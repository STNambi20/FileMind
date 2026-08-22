package com.filemind.ai.service;

import com.filemind.ai.dto.AiRewriteRequest;
import com.filemind.ai.dto.AiRewriteResponse;
import com.filemind.ai.dto.AiQuestionRequest;
import com.filemind.ai.dto.AiQuestionResponse;
import com.filemind.ai.dto.AiSummaryResponse;
import com.filemind.ai.dto.AiTagsResponse;
import com.filemind.activity.entity.ActivityAction;
import com.filemind.activity.service.ActivityService;
import com.filemind.common.exception.BadRequestException;
import com.filemind.document.entity.Document;
import com.filemind.document.service.DocumentService;
import com.filemind.user.entity.User;
import com.filemind.version.entity.DocumentVersion;
import com.filemind.version.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiService {

    private final AiOrchestratorService aiOrchestratorService;
    private final DocumentService documentService;
    private final VersionService versionService;
    private final ActivityService activityService;

    @Transactional
    public AiSummaryResponse summarize(User user, String documentId, int maxSentences) {
        if (maxSentences < 1 || maxSentences > 8) {
            throw new BadRequestException("Summary length must be between 1 and 8 sentences.");
        }
        Document document = documentService.getEditableDocument(user, documentId);
        DocumentVersion currentVersion = versionService.getCurrentVersion(documentId);
        String extractedText = requireExtractedText(currentVersion);
        AiSummaryResponse response = aiOrchestratorService.summarize(extractedText, maxSentences);
        aiOrchestratorService.applySummaryMetadata(currentVersion, response);
        activityService.log(ActivityAction.AI_SUMMARY, user, document, "Created a summary");
        return response;
    }

    @Transactional
    public AiTagsResponse tags(User user, String documentId) {
        Document document = documentService.getEditableDocument(user, documentId);
        DocumentVersion currentVersion = versionService.getCurrentVersion(documentId);
        String extractedText = requireExtractedText(currentVersion);
        AiTagsResponse response = aiOrchestratorService.tags(extractedText);
        aiOrchestratorService.applyTagsMetadata(currentVersion, response);
        activityService.log(ActivityAction.AI_TAGS, user, document, "Added tags");
        return response;
    }

    @Transactional
    public AiRewriteResponse rewrite(User user, String documentId, AiRewriteRequest request) {
        Document document = documentService.getEditableDocument(user, documentId);
        DocumentVersion currentVersion = versionService.getCurrentVersion(documentId);
        String extractedText = requireExtractedText(currentVersion);
        AiRewriteResponse response = aiOrchestratorService.rewrite(extractedText, request.mode());

        if (request.applyAsNewVersion()) {
            VersionService.VersionCreationResult versionResult = versionService.createAiRewriteVersion(document, user, response.rewrittenText(), request.mode());
            activityService.log(
                ActivityAction.AI_REWRITE,
                user,
                document,
                versionResult.created() ? versionResult.version() : null,
                versionResult.created() ? "Saved an AI rewrite as a new version" : "The AI rewrite matched the current content"
            );
            return new AiRewriteResponse(
                response.rewrittenText(),
                response.mode(),
                versionResult.created(),
                versionResult.version().getId(),
                versionResult.version().getVersionNumber()
            );
        }

        activityService.log(ActivityAction.AI_REWRITE, user, document, "Prepared an AI rewrite preview");
        return new AiRewriteResponse(response.rewrittenText(), response.mode(), false, null, null);
    }

    @Transactional
    public AiQuestionResponse ask(User user, String documentId, AiQuestionRequest request) {
        Document document = documentService.getReadableDocument(user, documentId);
        DocumentVersion currentVersion = versionService.getCurrentVersion(documentId);
        String extractedText = requireExtractedText(currentVersion);
        AiQuestionResponse response = aiOrchestratorService.answerQuestion(extractedText, request.question());
        activityService.log(ActivityAction.AI_QUESTION, user, document, currentVersion, "Asked a question about version " + currentVersion.getVersionNumber());
        return response;
    }

    private String requireExtractedText(DocumentVersion version) {
        if (version.getExtractedText() == null || version.getExtractedText().isBlank()) {
            throw new BadRequestException("This file does not contain readable text for AI actions.");
        }
        return version.getExtractedText();
    }
}
