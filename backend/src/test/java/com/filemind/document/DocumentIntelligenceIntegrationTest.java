package com.filemind.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.ai.dto.AiQuestionResponse;
import com.filemind.ai.dto.AiRewriteResponse;
import com.filemind.ai.dto.AiSummaryResponse;
import com.filemind.ai.dto.AiTagsResponse;
import com.filemind.ai.provider.AiProvider;
import com.filemind.support.DocumentProcessingTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:document-intelligence;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/document-intelligence",
    "filemind.storage.uploads=target/test-storage/document-intelligence/uploads",
    "filemind.storage.chunks=target/test-storage/document-intelligence/chunks",
    "filemind.storage.manifests=target/test-storage/document-intelligence/manifests"
})
@AutoConfigureMockMvc
class DocumentIntelligenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void detailsExposeSmartMetadataAndRelatedDocuments() throws Exception {
        String token = registerAndLogin("intelligence.owner@example.com");

        String firstDocumentId = uploadAndWait(
            token,
            "board-report.txt",
            "Board review scheduled for 12 March 2026. Gautam Kewat will review the finance policy and storage metrics."
        );
        uploadAndWait(
            token,
            "finance-review.txt",
            "Finance review notes for 12 March 2026. Gautam Kewat approved policy checks and storage metrics updates."
        );
        uploadAndWait(
            token,
            "design-notes.txt",
            "Typography updates for the design system and navigation refresh."
        );

        mockMvc.perform(
                get("/api/documents/{documentId}", firstDocumentId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.smartMetadata.dates[0]").value("12 March 2026"))
            .andExpect(jsonPath("$.data.smartMetadata.names[0]").value("Gautam Kewat"))
            .andExpect(jsonPath("$.data.smartMetadata.keywords.length()").value(org.hamcrest.Matchers.greaterThan(0)))
            .andExpect(jsonPath("$.data.relatedDocuments.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.data.relatedDocuments[0].name").value("finance-review.txt"));
    }

    private String uploadAndWait(String token, String fileName, String content) throws Exception {
        MvcResult upload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(new MockMultipartFile(
                        "file",
                        fileName,
                        "text/plain",
                        content.getBytes(StandardCharsets.UTF_8)
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
        return documentId;
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "Intelligence Owner",
                          "email": "%s",
                          "password": "Password1"
                        }
                        """.formatted(email))
            )
            .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(
                post("/api/auth/login")
                    .contentType(APPLICATION_JSON)
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
    static class StubAiProviderConfiguration {

        @Bean
        @Primary
        AiProvider aiProvider() {
            return new AiProvider() {
                @Override
                public AiSummaryResponse summarize(String text, int maxSentences) {
                    if (text.toLowerCase().contains("finance")) {
                        return new AiSummaryResponse(
                            "Finance policy review summary",
                            "Finance Policy Review",
                            List.of("Finance review is scheduled.", "Storage metrics are part of the review."),
                            List.of("Finance review is scheduled.", "Storage metrics are part of the review."),
                            "Readable for the review team."
                        );
                    }
                    return new AiSummaryResponse(
                        "Design notes summary",
                        "Design Notes",
                        List.of("Design changes are described."),
                        List.of("Design changes are described."),
                        "Readable for design teams."
                    );
                }

                @Override
                public AiTagsResponse tags(String text) {
                    if (text.toLowerCase().contains("finance")) {
                        return new AiTagsResponse(List.of("finance", "policy", "storage"));
                    }
                    return new AiTagsResponse(List.of("design", "navigation", "typography"));
                }

                @Override
                public AiRewriteResponse rewrite(String text, String mode) {
                    return new AiRewriteResponse("Rewrite for " + mode + ".", mode, false, null, null);
                }

                @Override
                public AiQuestionResponse answerQuestion(String text, String question) {
                    return new AiQuestionResponse(question, "Answer", text, true);
                }
            };
        }
    }
}
