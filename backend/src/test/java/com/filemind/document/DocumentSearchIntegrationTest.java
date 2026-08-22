package com.filemind.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.ai.dto.AiRewriteResponse;
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
    "spring.datasource.url=jdbc:h2:mem:document-search;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/document-search",
    "filemind.storage.uploads=target/test-storage/document-search/uploads",
    "filemind.storage.chunks=target/test-storage/document-search/chunks",
    "filemind.storage.manifests=target/test-storage/document-search/manifests"
})
@AutoConfigureMockMvc
class DocumentSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanStorage() throws IOException {
        Path root = Path.of("target/test-storage/document-search");
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
    void searchUsesAiMetadataAndExtractedTextRanking() throws Exception {
        String token = registerAndLogin("search.owner@example.com");

        String firstDocumentId = objectMapper.readTree(mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(new MockMultipartFile(
                        "file",
                        "minutes.txt",
                        "text/plain",
                        "Approval workflow and compliance checkpoints are ready for review.".getBytes(StandardCharsets.UTF_8)
                    ))
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString()).path("data").path("documentId").asText();
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, firstDocumentId);

        String secondDocumentId = objectMapper.readTree(mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(new MockMultipartFile(
                        "file",
                        "design-system.txt",
                        "text/plain",
                        "Spacing, layout hierarchy, and visual design guidance for the workspace.".getBytes(StandardCharsets.UTF_8)
                    ))
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString()).path("data").path("documentId").asText();
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, secondDocumentId);

        mockMvc.perform(
                get("/api/documents")
                    .param("query", "governance")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].name").value("minutes.txt"))
            .andExpect(jsonPath("$.data[0].searchSnippet").value(org.hamcrest.Matchers.containsStringIgnoringCase("Governance")));

        mockMvc.perform(
                get("/api/documents")
                    .param("query", "spacing")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].name").value("design-system.txt"))
            .andExpect(jsonPath("$.data[0].searchSnippet").value(org.hamcrest.Matchers.containsStringIgnoringCase("Spacing")));
    }

    @Test
    void dashboardAndDocumentDetailsUseSameMetricSourceOfTruthForSingleDocument() throws Exception {
        String token = registerAndLogin("metrics.owner@example.com");

        MvcResult upload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(new MockMultipartFile(
                        "file",
                        "metrics.txt",
                        "text/plain",
                        ("FileMind reuse check. ".repeat(400)).getBytes(StandardCharsets.UTF_8)
                    ))
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn();

        String documentId = objectMapper.readTree(upload.getResponse().getContentAsString())
            .path("data")
            .path("documentId")
            .asText();
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, documentId);

        JsonNode details = objectMapper.readTree(
            mockMvc.perform(get("/api/documents/{documentId}", documentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
        ).path("data");

        JsonNode dashboard = objectMapper.readTree(
            mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
        ).path("data");

        assertThat(dashboard.path("logicalStorageBytes").asLong()).isEqualTo(details.path("storage").path("logicalSize").asLong());
        assertThat(dashboard.path("actualStoredBytes").asLong()).isEqualTo(details.path("storage").path("actualStoredSize").asLong());
        assertThat(dashboard.path("savedBytes").asLong()).isEqualTo(details.path("storage").path("dedupSavedSize").asLong());
        assertThat(dashboard.path("uniqueChunkCount").asLong()).isEqualTo(details.path("storage").path("uniqueChunkCount").asLong());
        assertThat(dashboard.path("reusedChunkReferences").asLong()).isEqualTo(details.path("storage").path("reusedChunkReferences").asLong());
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "Search Owner",
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

        return objectMapper.readTree(login.getResponse().getContentAsString())
            .path("data")
            .path("accessToken")
            .asText();
    }

    @TestConfiguration
    static class StubAiClientConfiguration {

        @Bean
        @Primary
        AiProvider aiProvider() {
            return new AiProvider() {
                @Override
                public AiSummaryResponse summarize(String text, int maxSentences) {
                    if (text.toLowerCase().contains("approval workflow")) {
                        return new AiSummaryResponse(
                            "Governance review summary",
                            "Governance Review Brief",
                            List.of("Compliance checkpoints are ready.", "The approval workflow is prepared."),
                            List.of("Compliance checkpoints are ready.", "The approval workflow is prepared."),
                            "Readable for review teams."
                        );
                    }
                    return new AiSummaryResponse(
                        "Design operations summary",
                        "Design System Overview",
                        List.of("Spacing guidance is documented.", "Layout hierarchy is stable."),
                        List.of("Spacing guidance is documented.", "Layout hierarchy is stable."),
                        "Readable for product teams."
                    );
                }

                @Override
                public AiTagsResponse tags(String text) {
                    if (text.toLowerCase().contains("approval workflow")) {
                        return new AiTagsResponse(List.of("governance", "compliance", "workflow"));
                    }
                    return new AiTagsResponse(List.of("design", "spacing", "layout"));
                }

                @Override
                public AiRewriteResponse rewrite(String text, String mode) {
                    return new AiRewriteResponse("Rewritten content for " + mode + ".", mode, false, null, null);
                }
            };
        }
    }
}
