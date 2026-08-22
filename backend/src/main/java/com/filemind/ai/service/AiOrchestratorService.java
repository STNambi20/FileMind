package com.filemind.ai.service;

import com.filemind.ai.dto.AiQuestionResponse;
import com.filemind.ai.dto.AiRewriteResponse;
import com.filemind.ai.dto.AiSummaryResponse;
import com.filemind.ai.dto.AiTagsResponse;
import com.filemind.ai.provider.AiProvider;
import com.filemind.common.exception.ExternalServiceException;
import com.filemind.common.util.JsonListCodec;
import com.filemind.config.properties.AiServiceProperties;
import com.filemind.document.service.SmartMetadataExtractionService;
import com.filemind.settings.service.UserPreferenceService;
import com.filemind.user.entity.User;
import com.filemind.version.entity.DocumentVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiOrchestratorService {

    private final AiProvider aiProvider;
    private final JsonListCodec jsonListCodec;
    private final AiServiceProperties aiServiceProperties;
    private final SmartMetadataExtractionService smartMetadataExtractionService;
    private final UserPreferenceService userPreferenceService;

    public AiSummaryResponse summarize(String text, int maxSentences) {
        String preparedInput = prepareInput(text);
        try {
            return aiProvider.summarize(preparedInput, maxSentences);
        } catch (ExternalServiceException exception) {
            log.warn("AI summary provider unavailable. Falling back to local summary generation: {}", exception.getMessage());
            return fallbackSummary(preparedInput, maxSentences);
        }
    }

    public AiTagsResponse tags(String text) {
        String preparedInput = prepareInput(text);
        try {
            return aiProvider.tags(preparedInput);
        } catch (ExternalServiceException exception) {
            log.warn("AI tag provider unavailable. Falling back to local keyword extraction: {}", exception.getMessage());
            return fallbackTags(preparedInput);
        }
    }

    public AiRewriteResponse rewrite(String text, String mode) {
        return aiProvider.rewrite(prepareInput(text), mode);
    }

    public AiQuestionResponse answerQuestion(String text, String question) {
        try {
            return aiProvider.answerQuestion(prepareInput(text), question == null ? "" : question.trim());
        } catch (UnsupportedOperationException exception) {
            throw new ExternalServiceException("Question answering is not available for the configured AI provider.", exception);
        }
    }

    public void populateVersionMetadata(DocumentVersion version, User actor) {
        String extractedText = version.getExtractedText();
        if (extractedText == null || extractedText.isBlank()) {
            return;
        }

        applySmartMetadata(version);

        try {
            applySummaryMetadata(version, summarize(extractedText, 3));
        } catch (Exception ignored) {
            // AI enrichment remains best-effort so storage/versioning stay reliable.
        }

        if (userPreferenceService.getPreferences(actor).isAutoGenerateTagsOnUpload()) {
            try {
                applyTagsMetadata(version, tags(extractedText));
            } catch (Exception ignored) {
                // Tags remain optional enrichment metadata.
            }
        }
    }

    public void applySummaryMetadata(DocumentVersion version, AiSummaryResponse response) {
        version.setSummary(response.summary());
        version.setTitleSuggestion(response.titleSuggestion());
        version.setInsightsJson(jsonListCodec.write(response.keyInsights()));
        version.setHighlightsJson(jsonListCodec.write(response.bulletHighlights()));
        version.setReadabilityNote(response.readabilityNote());
    }

    public void applyTagsMetadata(DocumentVersion version, AiTagsResponse response) {
        version.setTagsJson(jsonListCodec.write(response.tags()));
    }

    public void applySmartMetadata(DocumentVersion version) {
        SmartMetadataExtractionService.DocumentInsightMetadata metadata = smartMetadataExtractionService.extract(version.getExtractedText());
        version.setDatesJson(jsonListCodec.write(metadata.dates()));
        version.setNamesJson(jsonListCodec.write(metadata.names()));
        version.setKeywordsJson(jsonListCodec.write(metadata.keywords()));
    }

    private String prepareInput(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replaceAll("[\\t\\x0B\\f]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .replaceAll(" {2,}", " ")
            .trim();

        int maxLength = Math.max(2_000, aiServiceProperties.getMaxInputCharacters());
        if (normalized.length() <= maxLength) {
            return normalized;
        }

        int boundary = normalized.lastIndexOf(' ', maxLength);
        if (boundary < maxLength / 2) {
            boundary = maxLength;
        }
        return normalized.substring(0, boundary).trim();
    }

    private AiSummaryResponse fallbackSummary(String text, int maxSentences) {
        SmartMetadataExtractionService.DocumentInsightMetadata metadata = smartMetadataExtractionService.extract(text);
        List<String> sentences = splitSentences(text);
        List<String> selectedSentences = sentences.stream()
            .limit(Math.max(1, maxSentences))
            .toList();

        String summary = selectedSentences.isEmpty()
            ? "No readable text was available to summarize."
            : String.join(" ", selectedSentences);

        String titleSuggestion = metadata.keywords().isEmpty()
            ? null
            : toTitle(metadata.keywords().stream().limit(3).toList());

        List<String> highlights = selectedSentences.isEmpty()
            ? List.of("Open the document to review the extracted text.")
            : selectedSentences.stream().limit(3).toList();

        List<String> keyInsights = new ArrayList<>();
        if (!metadata.names().isEmpty()) {
            keyInsights.add("People or teams mentioned: " + String.join(", ", metadata.names().stream().limit(3).toList()) + ".");
        }
        if (!metadata.dates().isEmpty()) {
            keyInsights.add("Dates mentioned: " + String.join(", ", metadata.dates().stream().limit(3).toList()) + ".");
        }
        if (keyInsights.isEmpty() && !highlights.isEmpty()) {
            keyInsights.add(highlights.get(0));
        }

        return new AiSummaryResponse(
            summary,
            titleSuggestion,
            keyInsights,
            highlights,
            "Generated from extracted document text while the AI service was unavailable."
        );
    }

    private AiTagsResponse fallbackTags(String text) {
        SmartMetadataExtractionService.DocumentInsightMetadata metadata = smartMetadataExtractionService.extract(text);
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.addAll(metadata.keywords().stream().limit(5).toList());
        tags.addAll(metadata.names().stream().limit(2).toList());
        tags.addAll(metadata.dates().stream().limit(2).toList());

        if (tags.isEmpty()) {
            splitSentences(text).stream()
                .findFirst()
                .ifPresent(firstSentence -> tags.add(firstSentence.length() > 40 ? firstSentence.substring(0, 40).trim() : firstSentence.trim()));
        }

        return new AiTagsResponse(tags.stream().limit(8).toList());
    }

    private List<String> splitSentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(text.split("(?<=[.!?])\\s+|\\n+"))
            .map(String::trim)
            .filter(sentence -> !sentence.isBlank())
            .toList();
    }

    private String toTitle(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }

        return keywords.stream()
            .map(keyword -> keyword.isBlank() ? keyword : Character.toUpperCase(keyword.charAt(0)) + keyword.substring(1))
            .reduce((left, right) -> left + " - " + right)
            .orElse(null);
    }
}
