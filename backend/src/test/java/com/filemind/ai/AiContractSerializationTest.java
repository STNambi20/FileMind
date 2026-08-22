package com.filemind.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.ai.dto.AiRewriteResponse;
import com.filemind.ai.dto.AiSummaryRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiContractSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializeSummaryRequestUsingSnakeCaseForFastApiContract() throws Exception {
        String json = objectMapper.writeValueAsString(new AiSummaryRequest("FileMind text", 2));

        assertThat(json).contains("\"max_sentences\":2");
        assertThat(json).doesNotContain("maxSentences");
    }

    @Test
    void deserializeRewriteResponseUsingSnakeCaseFromFastApi() throws Exception {
        AiRewriteResponse response = objectMapper.readValue(
            """
                {
                  "rewritten_text": "Rewritten FileMind content",
                  "mode": "professional"
                }
                """,
            AiRewriteResponse.class
        );

        assertThat(response.rewrittenText()).isEqualTo("Rewritten FileMind content");
        assertThat(response.mode()).isEqualTo("professional");
    }
}
