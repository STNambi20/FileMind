package com.filemind.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.ai.dto.AiQuestionResponse;
import com.filemind.ai.dto.AiSummaryResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiResponseDtoDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void summaryResponseMapsSnakeCaseFieldsFromAiService() throws Exception {
        AiSummaryResponse response = objectMapper.readValue(
            """
                {
                  "summary": "Short summary",
                  "title_suggestion": "Policy Review Brief",
                  "key_insights": ["Insight one", "Insight two"],
                  "bullet_highlights": ["Highlight one", "Highlight two"],
                  "readability_note": "Readable structure with clear sentence flow."
                }
                """,
            AiSummaryResponse.class
        );

        assertThat(response.summary()).isEqualTo("Short summary");
        assertThat(response.titleSuggestion()).isEqualTo("Policy Review Brief");
        assertThat(response.keyInsights()).containsExactly("Insight one", "Insight two");
        assertThat(response.bulletHighlights()).containsExactly("Highlight one", "Highlight two");
        assertThat(response.readabilityNote()).isEqualTo("Readable structure with clear sentence flow.");
    }

    @Test
    void questionResponseMapsSnakeCaseEvidenceSnippet() throws Exception {
        AiQuestionResponse response = objectMapper.readValue(
            """
                {
                  "question": "What is the review deadline?",
                  "answer": "Review deadline: 30 April 2026.",
                  "evidence_snippet": "Review deadline: 30 April 2026. Owner: Gautam Kewat.",
                  "answered": true
                }
                """,
            AiQuestionResponse.class
        );

        assertThat(response.question()).isEqualTo("What is the review deadline?");
        assertThat(response.answer()).isEqualTo("Review deadline: 30 April 2026.");
        assertThat(response.evidenceSnippet()).contains("30 April 2026");
        assertThat(response.answered()).isTrue();
    }
}
