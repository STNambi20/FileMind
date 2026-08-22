package com.filemind.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.document.entity.Document;
import com.filemind.document.repository.DocumentRepository;
import com.filemind.support.DocumentProcessingTestSupport;
import com.filemind.user.entity.User;
import com.filemind.user.repository.UserRepository;
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
    "spring.datasource.url=jdbc:h2:mem:dashboard-stability;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/dashboard-stability",
    "filemind.storage.uploads=target/test-storage/dashboard-stability/uploads",
    "filemind.storage.chunks=target/test-storage/dashboard-stability/chunks",
    "filemind.storage.manifests=target/test-storage/dashboard-stability/manifests"
})
@AutoConfigureMockMvc
class DashboardStabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanStorage() throws IOException {
        Path root = Path.of("target/test-storage/dashboard-stability");
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
    void dashboardAndDocumentListStayAvailableWhenLegacyOrphanDocumentExists() throws Exception {
        String email = "stability.owner@example.com";
        String token = registerAndLogin(email);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        Document orphan = new Document();
        orphan.setName("legacy-orphan.txt");
        orphan.setOwner(user);
        documentRepository.save(orphan);

        MvcResult upload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(new MockMultipartFile(
                        "file",
                        "live-document.txt",
                        "text/plain",
                        "stable dashboard content".getBytes(StandardCharsets.UTF_8)
                    ))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionCreated").value(true))
            .andReturn();
        String documentId = objectMapper.readTree(upload.getResponse().getContentAsString()).path("data").path("documentId").asText();
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, documentId);

        mockMvc.perform(
                get("/api/dashboard")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalFiles").value(1))
            .andExpect(jsonPath("$.data.recentDocuments[0].name").value("live-document.txt"));

        MvcResult documentList = mockMvc.perform(
                get("/api/documents")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andReturn();

        String responseBody = documentList.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("legacy-orphan.txt");
        assertThat(responseBody).contains("live-document.txt");
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "Stability Owner",
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
