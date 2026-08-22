package com.filemind.dashboard;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:dashboard-recent-access;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/dashboard-recent-access",
    "filemind.storage.uploads=target/test-storage/dashboard-recent-access/uploads",
    "filemind.storage.chunks=target/test-storage/dashboard-recent-access/chunks",
    "filemind.storage.manifests=target/test-storage/dashboard-recent-access/manifests"
})
@AutoConfigureMockMvc
class DashboardRecentAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanStorage() throws IOException {
        Path root = Path.of("target/test-storage/dashboard-recent-access");
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
    void dashboardReturnsRecentlyViewedDocumentsFromBackendState() throws Exception {
        String token = registerAndLogin("dashboard.viewer@example.com");

        MvcResult upload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(new MockMultipartFile(
                        "file",
                        "access-log.txt",
                        "text/plain",
                        "FileMind access trail verification".getBytes(StandardCharsets.UTF_8)
                    ))
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
            .andExpect(status().isOk());

        mockMvc.perform(
                get("/api/dashboard")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.recentAccessDocuments[0].id").value(documentId))
            .andExpect(jsonPath("$.data.recentAccessDocuments[0].name").value("access-log.txt"))
            .andExpect(jsonPath("$.data.recentAccessDocuments[0].lastViewedByEmail").value("dashboard.viewer@example.com"))
            .andExpect(jsonPath("$.data.recentAccessDocuments[0].lastViewedAt").isNotEmpty());
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "Dashboard Viewer",
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
