package com.filemind.ai.client;

import com.filemind.ai.dto.AiRewriteResponse;
import com.filemind.ai.dto.AiQuestionResponse;
import com.filemind.ai.dto.AiSummaryRequest;
import com.filemind.ai.dto.AiSummaryResponse;
import com.filemind.ai.dto.AiTagsResponse;
import com.filemind.ai.provider.AiProvider;
import com.filemind.common.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiClient implements AiProvider {

    private final RestClient aiRestClient;

    @Override
    public AiSummaryResponse summarize(String text, int maxSentences) {
        try {
            return aiRestClient.post()
                .uri("/summarize")
                .body(new AiSummaryRequest(text, maxSentences))
                .retrieve()
                .body(AiSummaryResponse.class);
        } catch (Exception exception) {
            throw new ExternalServiceException("Unable to summarize document content right now", exception);
        }
    }

    @Override
    public AiTagsResponse tags(String text) {
        try {
            return aiRestClient.post()
                .uri("/tags")
                .body(Map.of("text", text))
                .retrieve()
                .body(AiTagsResponse.class);
        } catch (Exception exception) {
            throw new ExternalServiceException("Unable to generate tags right now", exception);
        }
    }

    @Override
    public AiRewriteResponse rewrite(String text, String mode) {
        try {
            return aiRestClient.post()
                .uri("/rewrite")
                .body(Map.of("text", text, "mode", mode))
                .retrieve()
                .body(AiRewriteResponse.class);
        } catch (Exception exception) {
            throw new ExternalServiceException("Unable to rewrite document content right now", exception);
        }
    }

    @Override
    public AiQuestionResponse answerQuestion(String text, String question) {
        try {
            return aiRestClient.post()
                .uri("/ask")
                .body(Map.of("text", text, "question", question))
                .retrieve()
                .body(AiQuestionResponse.class);
        } catch (Exception exception) {
            throw new ExternalServiceException("Unable to answer document questions right now", exception);
        }
    }
}
