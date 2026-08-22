package com.filemind.settings;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:user-settings;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/user-settings",
    "filemind.storage.uploads=target/test-storage/user-settings/uploads",
    "filemind.storage.chunks=target/test-storage/user-settings/chunks",
    "filemind.storage.manifests=target/test-storage/user-settings/manifests"
})
@AutoConfigureMockMvc
class SettingsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void settingsPersistAndAffectUploadTimeAiMetadata() throws Exception {
        String token = registerAndLogin("settings.owner@example.com");

        mockMvc.perform(
                get("/api/settings")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.preferences.summaryLength").value("DETAILED"))
            .andExpect(jsonPath("$.data.preferences.autoGenerateTagsOnUpload").value(true))
            .andExpect(jsonPath("$.data.account.avatarUrl").isEmpty());

        mockMvc.perform(
                patch("/api/settings/account")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "Settings Owner",
                          "avatarUrl": "https://example.com/avatar.png"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.account.fullName").value("Settings Owner"))
            .andExpect(jsonPath("$.data.account.avatarUrl").value("https://example.com/avatar.png"));

        mockMvc.perform(
                get("/api/auth/me")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/avatar.png"));

        mockMvc.perform(
                patch("/api/settings/preferences")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "summaryLength": "SHORT",
                          "rewriteMode": "SIMPLE",
                          "autoGenerateTagsOnUpload": false,
                          "openLatestVersionAutomatically": true,
                          "wrapTextForTextFiles": false,
                          "defaultOpenMode": "DOWNLOAD",
                          "autoRetryFailedProcessing": false,
                          "keepFailedUploadsVisible": true,
                          "afterUploadBehavior": "GO_TO_DETAILS",
                          "notifyUploadCompleted": false,
                          "notifyProcessingFailed": true,
                          "notifyVersionCreated": false,
                          "defaultShareExpiryDays": 7,
                          "defaultVisibility": "SHARED",
                          "layoutDensity": "COMPACT",
                          "reducedMotion": true,
                          "highContrastMode": true
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.preferences.summaryLength").value("SHORT"))
            .andExpect(jsonPath("$.data.preferences.rewriteMode").value("SIMPLE"))
            .andExpect(jsonPath("$.data.preferences.autoGenerateTagsOnUpload").value(false))
            .andExpect(jsonPath("$.data.preferences.defaultOpenMode").value("DOWNLOAD"))
            .andExpect(jsonPath("$.data.preferences.defaultVisibility").value("SHARED"));

        MvcResult upload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(new MockMultipartFile(
                        "file",
                        "settings-ai.txt",
                        "text/plain",
                        "Governance tags should not be added automatically for this upload.".getBytes(StandardCharsets.UTF_8)
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

        mockMvc.perform(
                get("/api/documents/{documentId}", documentId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tags.length()").value(0))
            .andExpect(jsonPath("$.data.smartMetadata.keywords.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "Settings User",
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

        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }

    @TestConfiguration
    static class StubAiProviderConfiguration {

        @Bean
        @Primary
        AiProvider aiProvider() {
            return new AiProvider() {
                @Override
                public AiSummaryResponse summarize(String text, int maxSentences) {
                    return new AiSummaryResponse(
                        "Governance summary",
                        "Governance Brief",
                        List.of("Governance work is captured."),
                        List.of("Governance work is captured."),
                        "Readable for reviewers."
                    );
                }

                @Override
                public AiTagsResponse tags(String text) {
                    return new AiTagsResponse(List.of("governance", "review"));
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
