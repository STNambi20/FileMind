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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
    "spring.datasource.url=jdbc:h2:mem:image-upload-flow;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/image-upload-flow",
    "filemind.storage.uploads=target/test-storage/image-upload-flow/uploads",
    "filemind.storage.chunks=target/test-storage/image-upload-flow/chunks",
    "filemind.storage.manifests=target/test-storage/image-upload-flow/manifests",
    "filemind.document-processing.ocr-enabled=false"
})
@AutoConfigureMockMvc
class ImageUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanStorage() throws IOException {
        Path root = Path.of("target/test-storage/image-upload-flow");
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
    void reusesIdenticalImagesAndExposesImageViewerMetadata() throws Exception {
        String token = registerAndLogin("image.owner@example.com");
        byte[] imageBytes = buildImage();

        MvcResult firstUpload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(new MockMultipartFile("file", "diagram.png", "image/png", imageBytes))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn();

        String firstDocumentId = objectMapper.readTree(firstUpload.getResponse().getContentAsString())
            .path("data")
            .path("documentId")
            .asText();
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, firstDocumentId);

        MvcResult secondUpload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(new MockMultipartFile("file", "diagram-copy.png", "image/png", imageBytes))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn();

        String secondDocumentId = objectMapper.readTree(secondUpload.getResponse().getContentAsString())
            .path("data")
            .path("documentId")
            .asText();
        DocumentProcessingTestSupport.waitForTerminalStatus(mockMvc, objectMapper, token, secondDocumentId);

        mockMvc.perform(
                get("/api/documents/{documentId}", firstDocumentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentVersion.mediaType").value("image/png"))
            .andExpect(jsonPath("$.data.currentVersion.ocrApplied").value(false))
            .andExpect(jsonPath("$.data.extractedTextPreview").value(org.hamcrest.Matchers.containsString("Dimensions: 320 x 180 pixels")))
            .andExpect(jsonPath("$.data.extractedTextPreview").value(org.hamcrest.Matchers.containsString("Perceptual hash")));

        mockMvc.perform(
                get("/api/documents/{documentId}/viewer", firstDocumentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.previewMode").value("IMAGE"))
            .andExpect(jsonPath("$.data.version.ocrApplied").value(false))
            .andExpect(jsonPath("$.data.previewText").value(org.hamcrest.Matchers.containsString("Orientation: landscape")));

        MvcResult diagnostics = mockMvc.perform(
                get("/api/storage/diagnostics")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn();

        JsonNode metrics = objectMapper.readTree(diagnostics.getResponse().getContentAsString()).path("data");
        assertThat(metrics.path("totalChunks").asLong()).isEqualTo(2L);
        assertThat(metrics.path("uniqueChunks").asLong()).isEqualTo(1L);
        assertThat(metrics.path("reusedChunks").asLong()).isEqualTo(1L);
        assertThat(metrics.path("savedBytes").asLong()).isGreaterThan(0L);

        byte[] downloaded = mockMvc.perform(
                get("/api/documents/{documentId}/download", secondDocumentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

        assertThat(downloaded).isEqualTo(imageBytes);
    }

    private byte[] buildImage() throws IOException {
        BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(20, 40, 84));
        graphics.fillRect(0, 0, 320, 180);
        graphics.setColor(new Color(229, 231, 235));
        graphics.fillRoundRect(24, 28, 186, 64, 14, 14);
        graphics.setColor(new Color(59, 130, 246));
        graphics.fillRoundRect(24, 108, 256, 36, 18, 18);
        graphics.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "Image Owner",
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
