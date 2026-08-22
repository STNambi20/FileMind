package com.filemind.workflow;

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
    "spring.datasource.url=jdbc:h2:mem:end-to-end-workflow;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/end-to-end-workflow",
    "filemind.storage.uploads=target/test-storage/end-to-end-workflow/uploads",
    "filemind.storage.chunks=target/test-storage/end-to-end-workflow/chunks",
    "filemind.storage.manifests=target/test-storage/end-to-end-workflow/manifests"
})
@AutoConfigureMockMvc
class EndToEndSystemWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanStorage() throws IOException {
        Path root = Path.of("target/test-storage/end-to-end-workflow");
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
    void fullWorkflowUsesRealUploadVersionDashboardAiAndDownloadFlow() throws Exception {
        String token = registerAndLogin("workflow.owner@example.com");

        byte[] firstVersion = """
            FileMind stores only unique content blocks and reuses them across versions.
            This draft is ready for internal review.
            """.getBytes(StandardCharsets.UTF_8);

        MvcResult firstUpload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(new MockMultipartFile("file", "workflow.txt", "text/plain", firstVersion))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionCreated").value(true))
            .andExpect(jsonPath("$.data.versionNumber").value(1))
            .andReturn();

        JsonNode uploadBody = objectMapper.readTree(firstUpload.getResponse().getContentAsString()).path("data");
        String documentId = uploadBody.path("documentId").asText();
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, documentId);

        mockMvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalFiles").value(1))
            .andExpect(jsonPath("$.data.totalVersions").value(1))
            .andExpect(jsonPath("$.data.aiProcessedCount").value(1));

        byte[] secondVersion = """
            FileMind stores only unique content blocks and reuses them across versions.
            This draft is ready for internal and compliance review.
            """.getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(
                multipart("/api/documents/{documentId}/versions/upload", documentId)
                    .file(new MockMultipartFile("file", "workflow-v2.txt", "text/plain", secondVersion))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionCreated").value(true))
            .andExpect(jsonPath("$.data.versionNumber").value(2));
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, documentId);

        mockMvc.perform(get("/api/documents/{documentId}", documentId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalVersions").value(2))
            .andExpect(jsonPath("$.data.currentVersion.versionNumber").value(2))
            .andExpect(jsonPath("$.data.storage.dedupSavedSize").isNumber())
            .andExpect(jsonPath("$.data.storage.reusedChunkReferences").isNumber());

        mockMvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalFiles").value(1))
            .andExpect(jsonPath("$.data.totalVersions").value(2))
            .andExpect(jsonPath("$.data.savedBytes").isNumber())
            .andExpect(jsonPath("$.data.recentDocuments[0].currentVersionNumber").value(2));

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
            .andExpect(jsonPath("$.data.versionCreated").value(true))
            .andExpect(jsonPath("$.data.versionNumber").value(3))
            .andExpect(jsonPath("$.data.rewrittenText").value("This revised version is ready for the latest review cycle."));

        mockMvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalFiles").value(1))
            .andExpect(jsonPath("$.data.totalVersions").value(3))
            .andExpect(jsonPath("$.data.recentDocuments[0].currentVersionNumber").value(3))
            .andExpect(jsonPath("$.data.recentActivity.length()").isNumber());

        MvcResult download = mockMvc.perform(
                get("/api/documents/{documentId}/download", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn();

        assertThat(download.getResponse().getContentAsByteArray())
            .isEqualTo("This revised version is ready for the latest review cycle.".getBytes(StandardCharsets.UTF_8));
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "Workflow Owner",
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
                    return new AiSummaryResponse(
                        "Workflow summary",
                        "Workflow Brief",
                        List.of("Storage reuse is active.", "The document is awaiting review."),
                        List.of("Storage reuse is active.", "The document is awaiting review."),
                        "Clear for a standard review audience."
                    );
                }

                @Override
                public AiTagsResponse tags(String text) {
                    return new AiTagsResponse(List.of("workflow", "review", "storage"));
                }

                @Override
                public AiRewriteResponse rewrite(String text, String mode) {
                    String rewritten = switch (mode) {
                        case "formal" -> "This version has been prepared for formal review and sign-off.";
                        case "simplify" -> "This version is easier to read and review.";
                        case "shorten" -> "Ready for review.";
                        case "key-points" -> "- Storage reuse is active.\n- Review is still pending.";
                        default -> "This revised version is ready for the latest review cycle.";
                    };
                    return new AiRewriteResponse(rewritten, mode, false, null, null);
                }
            };
        }
    }
}
