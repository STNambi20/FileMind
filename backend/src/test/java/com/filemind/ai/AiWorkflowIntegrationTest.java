package com.filemind.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.ai.dto.AiRewriteResponse;
import com.filemind.ai.dto.AiQuestionResponse;
import com.filemind.ai.dto.AiSummaryResponse;
import com.filemind.ai.dto.AiTagsResponse;
import com.filemind.ai.provider.AiProvider;
import com.filemind.support.DocumentProcessingTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ai-workflow;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/ai-workflow",
    "filemind.storage.uploads=target/test-storage/ai-workflow/uploads",
    "filemind.storage.chunks=target/test-storage/ai-workflow/chunks",
    "filemind.storage.manifests=target/test-storage/ai-workflow/manifests",
    "filemind.storage.chunk-size=8"
})
@AutoConfigureMockMvc
class AiWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanStorage() throws IOException {
        Path root = Path.of("target/test-storage/ai-workflow");
        if (Files.exists(root)) {
            try (var stream = Files.walk(root)) {
                stream.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }

        Files.createDirectories(root.resolve("uploads"));
        Files.createDirectories(root.resolve("chunks"));
        Files.createDirectories(root.resolve("manifests"));
    }

    @Test
    void summaryTagsAndRewriteAreOrchestratedThroughBackend() throws Exception {
        String token = registerAndLogin("ai.owner@example.com");
        byte[] originalContent = "Original policy content for testing.".getBytes(StandardCharsets.UTF_8);

        MvcResult upload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(new MockMultipartFile("file", "policy.txt", "text/plain", originalContent))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionCreated").value(true))
            .andReturn();

        String documentId = objectMapper.readTree(upload.getResponse().getContentAsString())
            .path("data")
            .path("documentId")
            .asText();
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, documentId);

        mockMvc.perform(
                get("/api/documents/{documentId}", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary").value("Structured summary"))
            .andExpect(jsonPath("$.data.titleSuggestion").value("Policy Review Brief"))
            .andExpect(jsonPath("$.data.keyInsights.length()").value(2))
            .andExpect(jsonPath("$.data.bulletHighlights.length()").value(2))
            .andExpect(jsonPath("$.data.readabilityNote").value("Reads clearly for cross-functional teams."))
            .andExpect(jsonPath("$.data.tags[0]").value("policy"));

        mockMvc.perform(
                post("/api/documents/{documentId}/ai/summary", documentId)
                    .param("maxSentences", "2")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary").value("Structured summary"))
            .andExpect(jsonPath("$.data.titleSuggestion").value("Policy Review Brief"));

        mockMvc.perform(
                post("/api/documents/{documentId}/ai/summary", documentId)
                    .param("maxSentences", "0")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Summary length must be between 1 and 8 sentences."));

        mockMvc.perform(
                post("/api/documents/{documentId}/ai/tags", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tags.length()").value(3))
            .andExpect(jsonPath("$.data.tags[0]").value("policy"));

        mockMvc.perform(
                post("/api/documents/{documentId}/ai/ask", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "question": "What is the current policy baseline?"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.answered").value(true))
            .andExpect(jsonPath("$.data.answer").value("The document defines the current policy baseline."))
            .andExpect(jsonPath("$.data.evidenceSnippet").value(org.hamcrest.Matchers.containsString("current policy baseline")));

        mockMvc.perform(
                post("/api/documents/{documentId}/ai/rewrite", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "mode": "formal",
                          "applyAsNewVersion": false
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mode").value("formal"))
            .andExpect(jsonPath("$.data.rewrittenText").value("Formal policy content prepared for executive review."));

        mockMvc.perform(
                post("/api/documents/{documentId}/ai/rewrite", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "mode": "readability",
                          "applyAsNewVersion": false
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mode").value("readability"))
            .andExpect(jsonPath("$.data.rewrittenText").value("This version reads more smoothly and is easier to review quickly."));

        mockMvc.perform(
                post("/api/documents/{documentId}/ai/rewrite", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "mode": "professional",
                          "applyAsNewVersion": true
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rewrittenText").value("Rewritten policy content for the latest version."))
            .andExpect(jsonPath("$.data.versionCreated").value(true))
            .andExpect(jsonPath("$.data.versionNumber").value(2));

        mockMvc.perform(
                get("/api/documents/{documentId}/versions", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].versionNumber").value(2));

        mockMvc.perform(
                get("/api/documents/{documentId}/download", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                .isEqualTo("Rewritten policy content for the latest version.".getBytes(StandardCharsets.UTF_8)));
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "AI Owner",
                          "email": "%s",
                          "password": "Password1"
                        }
                        """.formatted(email))
            )
            .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "%s",
                          "password": "Password1"
                        }
                        """.formatted(email))
            )
            .andExpect(status().isOk())
            .andReturn();

        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }

    @TestConfiguration
    static class StubAiClientConfiguration {

        @Bean
        @Primary
        AiProvider aiProvider() {
            return new AiProvider() {
                @Override
                public AiSummaryResponse summarize(String text, int maxSentences) {
                    return new AiSummaryResponse(
                        "Structured summary",
                        "Policy Review Brief",
                        List.of("The document defines the current policy baseline.", "The review flow still needs approval."),
                        List.of("Current policy baseline is documented.", "Approval review is still pending."),
                        "Reads clearly for cross-functional teams."
                    );
                }

                @Override
                public AiTagsResponse tags(String text) {
                    return new AiTagsResponse(List.of("policy", "draft", "review"));
                }

                @Override
                public AiRewriteResponse rewrite(String text, String mode) {
                    String rewrittenText = switch (mode) {
                        case "formal" -> "Formal policy content prepared for executive review.";
                        case "readability" -> "This version reads more smoothly and is easier to review quickly.";
                        default -> "Rewritten policy content for the latest version.";
                    };
                    return new AiRewriteResponse(rewrittenText, mode, false, null, null);
                }

                @Override
                public AiQuestionResponse answerQuestion(String text, String question) {
                    return new AiQuestionResponse(
                        question,
                        "The document defines the current policy baseline.",
                        "The document defines the current policy baseline. The review flow still needs approval.",
                        true
                    );
                }
            };
        }
    }
}
