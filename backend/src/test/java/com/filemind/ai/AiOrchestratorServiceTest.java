package com.filemind.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.ai.dto.AiQuestionResponse;
import com.filemind.ai.dto.AiRewriteResponse;
import com.filemind.ai.dto.AiSummaryResponse;
import com.filemind.ai.dto.AiTagsResponse;
import com.filemind.ai.provider.AiProvider;
import com.filemind.ai.service.AiOrchestratorService;
import com.filemind.common.exception.ExternalServiceException;
import com.filemind.common.util.JsonListCodec;
import com.filemind.config.properties.AiServiceProperties;
import com.filemind.document.service.SmartMetadataExtractionService;
import com.filemind.settings.repository.UserPreferenceRepository;
import com.filemind.settings.service.UserPreferenceService;
import com.filemind.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AiOrchestratorServiceTest {

    @Test
    void askDocumentReturnsControlledErrorWhenProviderDoesNotSupportQuestions() {
        AiServiceProperties properties = new AiServiceProperties();
        properties.setMaxInputCharacters(1000);

        AiOrchestratorService orchestratorService = new AiOrchestratorService(
            new SummaryOnlyProvider(),
            new JsonListCodec(new ObjectMapper()),
            properties,
            new SmartMetadataExtractionService(),
            new UserPreferenceService(mock(UserPreferenceRepository.class), mock(UserRepository.class))
        );

        assertThatThrownBy(() -> orchestratorService.answerQuestion("A readable document body.", "What is this about?"))
            .isInstanceOf(ExternalServiceException.class)
            .hasMessage("Question answering is not available for the configured AI provider.");
    }

    @Test
    void summarizeFallsBackToReadableLocalSummaryWhenProviderIsUnavailable() {
        AiServiceProperties properties = new AiServiceProperties();
        properties.setMaxInputCharacters(1000);

        AiOrchestratorService orchestratorService = new AiOrchestratorService(
            new FailingProvider(),
            new JsonListCodec(new ObjectMapper()),
            properties,
            new SmartMetadataExtractionService(),
            new UserPreferenceService(mock(UserPreferenceRepository.class), mock(UserRepository.class))
        );

        AiSummaryResponse response = orchestratorService.summarize(
            "Project review meeting is scheduled for 30 April 2026. Gautam Kewat will present the final demo.",
            2
        );

        assertThat(response.summary()).contains("Project review meeting is scheduled for 30 April 2026.");
        assertThat(response.readabilityNote()).contains("AI service was unavailable");
        assertThat(response.keyInsights()).isNotEmpty();
    }

    @Test
    void tagsFallBackToMetadataKeywordsWhenProviderIsUnavailable() {
        AiServiceProperties properties = new AiServiceProperties();
        properties.setMaxInputCharacters(1000);

        AiOrchestratorService orchestratorService = new AiOrchestratorService(
            new FailingProvider(),
            new JsonListCodec(new ObjectMapper()),
            properties,
            new SmartMetadataExtractionService(),
            new UserPreferenceService(mock(UserPreferenceRepository.class), mock(UserRepository.class))
        );

        AiTagsResponse response = orchestratorService.tags(
            "Project review meeting is scheduled for 30 April 2026. Gautam Kewat will present the final demo."
        );

        assertThat(response.tags()).isNotEmpty();
    }

    private static final class SummaryOnlyProvider implements AiProvider {
        @Override
        public AiSummaryResponse summarize(String text, int maxSentences) {
            return new AiSummaryResponse("Summary", "Title", List.of(), List.of(), "Readable.");
        }

        @Override
        public AiTagsResponse tags(String text) {
            return new AiTagsResponse(List.of("tag"));
        }

        @Override
        public AiRewriteResponse rewrite(String text, String mode) {
            return new AiRewriteResponse("Rewrite", mode, false, null, null);
        }

        @Override
        public AiQuestionResponse answerQuestion(String text, String question) {
            return AiProvider.super.answerQuestion(text, question);
        }
    }

    private static final class FailingProvider implements AiProvider {
        @Override
        public AiSummaryResponse summarize(String text, int maxSentences) {
            throw new ExternalServiceException("Summary provider offline");
        }

        @Override
        public AiTagsResponse tags(String text) {
            throw new ExternalServiceException("Tag provider offline");
        }

        @Override
        public AiRewriteResponse rewrite(String text, String mode) {
            throw new ExternalServiceException("Rewrite provider offline");
        }

        @Override
        public AiQuestionResponse answerQuestion(String text, String question) {
            throw new ExternalServiceException("Question provider offline");
        }
    }
}
