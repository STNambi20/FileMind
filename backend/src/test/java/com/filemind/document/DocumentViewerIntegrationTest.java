package com.filemind.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.support.DocumentProcessingTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:document-viewer-flow;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/document-viewer",
    "filemind.storage.uploads=target/test-storage/document-viewer/uploads",
    "filemind.storage.chunks=target/test-storage/document-viewer/chunks",
    "filemind.storage.manifests=target/test-storage/document-viewer/manifests"
})
@AutoConfigureMockMvc
class DocumentViewerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanStorage() throws IOException {
        Path root = Path.of("target/test-storage/document-viewer");
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
    void viewerTracksAccessAndSupportsVersionComparison() throws Exception {
        String token = registerAndLogin("viewer.owner@example.com");

        byte[] firstVersion = """
            FileMind now includes a dedicated in-app viewer.
            The first version is ready for review.
            """.getBytes(StandardCharsets.UTF_8);

        MvcResult upload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(new MockMultipartFile("file", "viewer.txt", "text/plain", firstVersion))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn();

        String documentId = objectMapper.readTree(upload.getResponse().getContentAsString()).path("data").path("documentId").asText();
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, documentId);

        mockMvc.perform(
                get("/api/documents/{documentId}/viewer", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.previewMode").value("TEXT"))
            .andExpect(jsonPath("$.data.version.versionNumber").value(1))
            .andExpect(jsonPath("$.data.previewText").value(org.hamcrest.Matchers.containsString("dedicated in-app viewer")))
            .andExpect(jsonPath("$.data.lastViewedByEmail").value("viewer.owner@example.com"))
            .andExpect(jsonPath("$.data.recentActivity[0].action").value("Document viewed"));

        mockMvc.perform(
                multipart("/api/documents/{documentId}/versions/upload", documentId)
                    .file(new MockMultipartFile(
                        "file",
                        "viewer-v2.txt",
                        "text/plain",
                        """
                            FileMind now includes a dedicated in-app viewer.
                            The second version is ready for review and approval.
                            """.getBytes(StandardCharsets.UTF_8)
                    ))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionNumber").value(2));
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, documentId);

        MvcResult versionsResponse = mockMvc.perform(
                get("/api/documents/{documentId}/versions", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn();

        JsonNode versions = objectMapper.readTree(versionsResponse.getResponse().getContentAsString()).path("data");
        String targetVersionId = versions.get(0).path("id").asText();
        String baseVersionId = versions.get(1).path("id").asText();

        MvcResult comparisonResponse = mockMvc.perform(
                get("/api/documents/{documentId}/versions/compare", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .param("baseVersionId", baseVersionId)
                    .param("targetVersionId", targetVersionId)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.comparable").value(true))
            .andExpect(jsonPath("$.data.addedLineCount").isNumber())
            .andExpect(jsonPath("$.data.metadataChanges.length()").isNumber())
            .andReturn();

        JsonNode comparison = objectMapper.readTree(comparisonResponse.getResponse().getContentAsString()).path("data");
        assertThat(comparison.path("addedLineCount").asInt()).isGreaterThan(0);

        mockMvc.perform(
                get("/api/versions/{versionId}/download", targetVersionId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk());

        MvcResult activityResponse = mockMvc.perform(
                get("/api/activity")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn();

        JsonNode activityItems = objectMapper.readTree(activityResponse.getResponse().getContentAsString()).path("data");
        assertThat(activityItems.toString()).contains("Document downloaded");
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "Viewer Owner",
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

        return objectMapper.readTree(login.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
