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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:dedup-versioning;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/dedup-versioning",
    "filemind.storage.uploads=target/test-storage/dedup-versioning/uploads",
    "filemind.storage.chunks=target/test-storage/dedup-versioning/chunks",
    "filemind.storage.manifests=target/test-storage/dedup-versioning/manifests",
    "filemind.storage.chunking-strategy=content-defined",
    "filemind.storage.cdc-min-chunk-size=2048",
    "filemind.storage.cdc-avg-chunk-size=4096",
    "filemind.storage.cdc-max-chunk-size=8192"
})
@AutoConfigureMockMvc
class DeduplicationVersioningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StoredChunkRepository storedChunkRepository;

    @BeforeEach
    void cleanStorage() throws IOException {
        Path root = Path.of("target/test-storage/dedup-versioning");
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
    void reusesChunksAcrossVersionsAndReconstructsDownloadsFromManifests() throws Exception {
        String token = registerAndLogin("dedup.owner@example.com");
        byte[] versionOneContent = buildDocument("Prepared for architecture review");
        byte[] versionTwoContent = buildDocument("Prepared for architecture and compliance review");

        MockMultipartFile versionOneFile = new MockMultipartFile("file", "base.txt", "text/plain", versionOneContent);
        MvcResult firstUpload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(versionOneFile)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionCreated").value(true))
            .andExpect(jsonPath("$.data.versionNumber").value(1))
            .andReturn();

        JsonNode firstUploadBody = objectMapper.readTree(firstUpload.getResponse().getContentAsString());
        String documentId = firstUploadBody.path("data").path("documentId").asText();
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, documentId);

        long initialPhysicalBytes = storedChunkRepository.totalStoredBytes();
        assertThat(storedChunkRepository.count()).isGreaterThan(2);
        assertThat(initialPhysicalBytes).isLessThan(versionOneContent.length);
        assertThat(countFiles("target/test-storage/dedup-versioning/manifests")).isEqualTo(1L);

        MockMultipartFile versionTwoFile = new MockMultipartFile("file", "base-v2.txt", "text/plain", versionTwoContent);
        mockMvc.perform(
                multipart("/api/documents/{documentId}/versions/upload", documentId)
                    .file(versionTwoFile)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionCreated").value(true))
            .andExpect(jsonPath("$.data.versionNumber").value(2));
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, documentId);

        long secondPhysicalBytes = storedChunkRepository.totalStoredBytes();
        long combinedLogicalBytes = versionOneContent.length + versionTwoContent.length;

        assertThat(secondPhysicalBytes).isLessThan(combinedLogicalBytes);
        assertThat(secondPhysicalBytes - initialPhysicalBytes).isLessThan(versionTwoContent.length / 3);
        assertThat(countFiles("target/test-storage/dedup-versioning/manifests")).isEqualTo(2L);

        MvcResult documentDetails = mockMvc.perform(
                get("/api/documents/{documentId}", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalVersions").value(2))
            .andExpect(jsonPath("$.data.currentVersion.versionNumber").value(2))
            .andReturn();

        JsonNode detailsBody = objectMapper.readTree(documentDetails.getResponse().getContentAsString()).path("data");
        assertThat(detailsBody.path("storage").path("logicalSize").asLong()).isEqualTo(combinedLogicalBytes);
        assertThat(detailsBody.path("storage").path("actualStoredSize").asLong()).isEqualTo(secondPhysicalBytes);
        assertThat(detailsBody.path("storage").path("dedupSavedSize").asLong()).isGreaterThan(16384L);
        assertThat(detailsBody.path("storage").path("actualStoredSize").asLong()).isLessThan(detailsBody.path("storage").path("logicalSize").asLong());
        assertThat(detailsBody.path("storage").path("uniqueChunkCount").asLong()).isLessThan(detailsBody.path("storage").path("totalChunkReferences").asLong());
        assertThat(detailsBody.path("currentVersion").path("newChunkCount").asInt()).isLessThan(detailsBody.path("currentVersion").path("totalChunkCount").asInt());
        assertThat(detailsBody.path("currentVersion").path("reusedChunkCount").asInt()).isGreaterThan(0);

        MvcResult versionsResponse = mockMvc.perform(
                get("/api/documents/{documentId}/versions", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].versionNumber").value(2))
            .andExpect(jsonPath("$.data[1].versionNumber").value(1))
            .andReturn();

        JsonNode versions = objectMapper.readTree(versionsResponse.getResponse().getContentAsString()).path("data");
        String versionTwoId = versions.get(0).path("id").asText();
        String versionOneId = versions.get(1).path("id").asText();

        mockMvc.perform(
                get("/api/documents/{documentId}/download", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/plain"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"base-v2.txt\""))
            .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(versionTwoContent));

        mockMvc.perform(
                get("/api/versions/{versionId}/download", versionOneId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/plain"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"base.txt\""))
            .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(versionOneContent));

        MvcResult duplicateUpload = mockMvc.perform(
                multipart("/api/documents/{documentId}/versions/upload", documentId)
                    .file(new MockMultipartFile("file", "base-v2-copy.txt", "text/plain", versionTwoContent))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionCreated").value(true))
            .andReturn();
        JsonNode duplicateStatus = DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, documentId);
        assertThat(duplicateStatus.path("currentVersionNumber").asInt()).isEqualTo(2);
        assertThat(duplicateStatus.path("totalVersions").asInt()).isEqualTo(2);

        mockMvc.perform(
                post("/api/versions/{versionId}/restore", versionOneId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionNumber").value(3));

        assertThat(storedChunkRepository.totalStoredBytes()).isEqualTo(secondPhysicalBytes);
        assertThat(countFiles("target/test-storage/dedup-versioning/manifests")).isEqualTo(3L);

        mockMvc.perform(
                get("/api/documents/{documentId}", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalVersions").value(3))
            .andExpect(jsonPath("$.data.currentVersion.versionNumber").value(3))
            .andExpect(jsonPath("$.data.currentVersion.actualStoredSize").value(0))
            .andExpect(jsonPath("$.data.currentVersion.newChunkCount").value(0));

        mockMvc.perform(
                get("/api/documents/{documentId}/download", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(versionOneContent));

        mockMvc.perform(
                get("/api/versions/{versionId}/download", versionTwoId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(versionTwoContent));
    }

    private byte[] buildDocument(String middleLine) {
        String lead = "FileMind stores only unique chunks while preserving exact reconstruction for every version. ".repeat(220);
        String tail = "Shared paragraphs stay stable across uploads so unchanged content can be reused efficiently. ".repeat(160);
        return (lead + middleLine + ". " + tail).getBytes(StandardCharsets.UTF_8);
    }

    private long countFiles(String directory) throws IOException {
        try (var stream = Files.list(Path.of(directory))) {
            return stream.count();
        }
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "Dedup Owner",
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
