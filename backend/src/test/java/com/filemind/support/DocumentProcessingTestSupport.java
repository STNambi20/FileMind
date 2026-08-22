package com.filemind.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.document.dto.DocumentProcessingStatusResponse;
import com.filemind.document.service.DocumentService;
import com.filemind.user.entity.User;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class DocumentProcessingTestSupport {

    private static final long POLL_DELAY_MILLIS = 250L;
    private static final int MAX_ATTEMPTS = 720;

    private DocumentProcessingTestSupport() {
    }

    public static JsonNode waitForTerminalStatus(MockMvc mockMvc,
                                                 ObjectMapper objectMapper,
                                                 String token,
                                                 String documentId) throws Exception {
        JsonNode latest = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            latest = objectMapper.readTree(
                mockMvc.perform(
                        get("/api/documents/{documentId}/processing-status", documentId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    )
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString()
            ).path("data");

            String status = latest.path("status").asText();
            if ("COMPLETED".equals(status)) {
                return latest;
            }
            if ("FAILED".equals(status)) {
                fail(latest.path("message").asText("Processing failed."));
            }
            Thread.sleep(POLL_DELAY_MILLIS);
        }

        fail("Processing did not complete for document " + documentId + " (last status: " + (latest == null ? "unknown" : latest.path("status").asText()) + ")");
        return latest;
    }

    public static DocumentProcessingStatusResponse waitForTerminalStatus(DocumentService documentService,
                                                                        User user,
                                                                        String documentId) throws Exception {
        DocumentProcessingStatusResponse latest = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            latest = documentService.getProcessingStatus(user, documentId);
            if ("COMPLETED".equals(latest.status())) {
                return latest;
            }
            if ("FAILED".equals(latest.status())) {
                fail(latest.message() == null ? "Processing failed." : latest.message());
            }
            Thread.sleep(POLL_DELAY_MILLIS);
        }

        fail("Processing did not complete for document " + documentId + " (last status: " + (latest == null ? "unknown" : latest.status()) + ")");
        return latest;
    }
}
