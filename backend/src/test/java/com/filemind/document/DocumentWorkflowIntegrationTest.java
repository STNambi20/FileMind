package com.filemind.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.chunk.repository.StoredChunkRepository;
import com.filemind.support.DocumentProcessingTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:document-flow;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/document",
    "filemind.storage.uploads=target/test-storage/document/uploads",
    "filemind.storage.chunks=target/test-storage/document/chunks",
    "filemind.storage.manifests=target/test-storage/document/manifests"
})
@AutoConfigureMockMvc
class DocumentWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StoredChunkRepository storedChunkRepository;

    @BeforeEach
    void cleanStorage() throws IOException {
        Path root = Path.of("target/test-storage/document");
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
        Files.createDirectories(Path.of("target/test-storage/document/uploads"));
        Files.createDirectories(Path.of("target/test-storage/document/chunks"));
        Files.createDirectories(Path.of("target/test-storage/document/manifests"));
    }

    @Test
    void reuseChunksAcrossDocumentsAndPreserveVersionHistory() throws Exception {
        String token = registerAndLogin("owner@example.com");

        MockMultipartFile firstFile = new MockMultipartFile(
            "file",
            "notes.txt",
            "text/plain",
            "Chunk deduplication test document.".getBytes()
        );

        MvcResult firstUpload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(firstFile)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionCreated").value(true))
            .andReturn();

        JsonNode firstUploadBody = objectMapper.readTree(firstUpload.getResponse().getContentAsString());
        String documentId = firstUploadBody.path("data").path("documentId").asText();
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, documentId);

        long initialChunkCount = storedChunkRepository.count();

        MockMultipartFile secondFile = new MockMultipartFile(
            "file",
            "copy.txt",
            "text/plain",
            "Chunk deduplication test document.".getBytes()
        );

        MvcResult secondUpload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(secondFile)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionCreated").value(true))
            .andReturn();
        String secondDocumentId = objectMapper.readTree(secondUpload.getResponse().getContentAsString())
            .path("data")
            .path("documentId")
            .asText();
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, secondDocumentId);
        assertThat(storedChunkRepository.count()).isEqualTo(initialChunkCount);

        MockMultipartFile changedVersion = new MockMultipartFile(
            "file",
            "notes-v2.txt",
            "text/plain",
            "Chunk deduplication test document with a new sentence.".getBytes()
        );

        mockMvc.perform(
                multipart("/api/documents/{documentId}/versions/upload", documentId)
                    .file(changedVersion)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionCreated").value(true))
            .andExpect(jsonPath("$.data.versionNumber").value(2));
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, documentId);

        mockMvc.perform(
                get("/api/documents/{documentId}/versions", documentId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2));

        MvcResult versionsResponse = mockMvc.perform(
                get("/api/documents/{documentId}/versions", documentId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn();

        var versionIds = objectMapper.readTree(versionsResponse.getResponse().getContentAsString())
            .path("data")
            .findValues("id");
        String versionOneId = versionIds.get(versionIds.size() - 1).asText();

        mockMvc.perform(
                post("/api/versions/{versionId}/restore", versionOneId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionNumber").value(3));
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "Owner User",
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
}
