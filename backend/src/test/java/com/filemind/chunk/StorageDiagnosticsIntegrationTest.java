package com.filemind.chunk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.chunk.entity.StoredChunk;
import com.filemind.chunk.model.ChunkStorageResult;
import com.filemind.chunk.repository.StoredChunkRepository;
import com.filemind.chunk.storage.StorageProvider;
import com.filemind.chunk.util.ChunkHashUtils;
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
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:storage-diagnostics;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/storage-diagnostics",
    "filemind.storage.uploads=target/test-storage/storage-diagnostics/uploads",
    "filemind.storage.chunks=target/test-storage/storage-diagnostics/chunks",
    "filemind.storage.manifests=target/test-storage/storage-diagnostics/manifests",
    "filemind.storage.chunking-strategy=content-defined",
    "filemind.storage.cdc-min-chunk-size=2048",
    "filemind.storage.cdc-avg-chunk-size=4096",
    "filemind.storage.cdc-max-chunk-size=8192"
})
@AutoConfigureMockMvc
class StorageDiagnosticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StoredChunkRepository storedChunkRepository;

    @Autowired
    private StorageProvider storageProvider;

    @BeforeEach
    void cleanStorage() throws IOException {
        Path root = Path.of("target/test-storage/storage-diagnostics");
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
    void reportsStorageDiagnosticsAndCleansOrphanedChunkObjects() throws Exception {
        String token = registerAndLogin("storage.owner@example.com");
        byte[] versionOneContent = buildDocument("Prepared for architecture review");
        byte[] versionTwoContent = buildDocument("Prepared for architecture and compliance review");

        MockMultipartFile versionOneFile = new MockMultipartFile("file", "report.txt", "text/plain", versionOneContent);
        MvcResult firstUpload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(versionOneFile)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn();

        String documentId = objectMapper.readTree(firstUpload.getResponse().getContentAsString())
            .path("data")
            .path("documentId")
            .asText();

        mockMvc.perform(
                multipart("/api/documents/{documentId}/versions/upload", documentId)
                    .file(new MockMultipartFile("file", "report-v2.txt", "text/plain", versionTwoContent))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk());

        Instant beforeDownload = storedChunkRepository.findAll().stream()
            .findFirst()
            .orElseThrow()
            .getLastUsedAt();

        mockMvc.perform(
                get("/api/documents/{documentId}/download", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk());

        Instant afterDownload = storedChunkRepository.findAll().stream()
            .findFirst()
            .orElseThrow()
            .getLastUsedAt();

        assertThat(afterDownload).isAfterOrEqualTo(beforeDownload);
        assertThat(storedChunkRepository.findAll()).allMatch(chunk -> chunk.getCreatedAt() != null && chunk.getLastUsedAt() != null);

        MvcResult diagnosticsBeforeCleanup = mockMvc.perform(
                get("/api/storage/diagnostics")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalChunks").isNumber())
            .andExpect(jsonPath("$.data.uniqueChunks").isNumber())
            .andExpect(jsonPath("$.data.reusedChunks").isNumber())
            .andReturn();

        JsonNode diagnosticsBeforeCleanupBody = objectMapper.readTree(diagnosticsBeforeCleanup.getResponse().getContentAsString()).path("data");
        assertThat(diagnosticsBeforeCleanupBody.path("averageChunkSizeBytes").asDouble()).isGreaterThan(2000D);
        assertThat(diagnosticsBeforeCleanupBody.path("compressionRatio").asDouble()).isLessThan(1D);
        assertThat(diagnosticsBeforeCleanupBody.path("dedupRatio").asDouble()).isGreaterThan(0D);

        byte[] orphanContent = ("orphaned chunk bytes ".repeat(120)).getBytes(StandardCharsets.UTF_8);
        String orphanHash = ChunkHashUtils.sha256(orphanContent);
        ChunkStorageResult orphanStorage = storageProvider.storeChunk(orphanHash, orphanContent);

        StoredChunk orphanChunk = new StoredChunk();
        orphanChunk.setHash(orphanHash);
        orphanChunk.setSizeBytes(orphanContent.length);
        orphanChunk.setStoredSizeBytes(orphanStorage.storedSizeBytes());
        orphanChunk.setStoragePath(orphanStorage.storagePath());
        orphanChunk.setReferenceCount(1L);
        orphanChunk.markUsed(Instant.now());
        storedChunkRepository.save(orphanChunk);

        mockMvc.perform(
                post("/api/storage/cleanup")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.removedChunkObjects").value(1))
            .andExpect(jsonPath("$.data.removedStoredBytes").value(org.hamcrest.Matchers.greaterThan(0)));

        assertThat(storedChunkRepository.findById(orphanHash)).isEmpty();

        MvcResult diagnosticsAfterCleanup = mockMvc.perform(
                get("/api/storage/diagnostics")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn();

        JsonNode diagnosticsBody = objectMapper.readTree(diagnosticsAfterCleanup.getResponse().getContentAsString()).path("data");
        assertThat(diagnosticsBody.path("totalStoredChunkObjects").asLong()).isEqualTo(diagnosticsBody.path("uniqueChunks").asLong());
    }

    private byte[] buildDocument(String middleLine) {
        String lead = "FileMind stores only unique chunks while preserving exact reconstruction for every version. ".repeat(180);
        String tail = "Shared paragraphs stay stable across uploads so unchanged content can be reused efficiently. ".repeat(120);
        return (lead + middleLine + ". " + tail).getBytes(StandardCharsets.UTF_8);
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "Storage Owner",
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
