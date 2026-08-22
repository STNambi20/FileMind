package com.filemind.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.support.DocumentProcessingTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:document-comments;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/document-comments",
    "filemind.storage.uploads=target/test-storage/document-comments/uploads",
    "filemind.storage.chunks=target/test-storage/document-comments/chunks",
    "filemind.storage.manifests=target/test-storage/document-comments/manifests"
})
@AutoConfigureMockMvc
class DocumentCommentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ownerCanAddDocumentAndVersionReviewNotes() throws Exception {
        String token = registerAndLogin("comments.owner@example.com");

        MvcResult upload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(new MockMultipartFile(
                        "file",
                        "review-notes.txt",
                        "text/plain",
                        "Review notes should stay with the document history.".getBytes()
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

        JsonNode versions = objectMapper.readTree(
            mockMvc.perform(
                    get("/api/documents/{documentId}/versions", documentId)
                        .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
        ).path("data");
        String versionId = versions.get(0).path("id").asText();

        mockMvc.perform(
                post("/api/documents/{documentId}/comments", documentId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "content": "Needs one final review before sharing."
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionNumber").isEmpty());

        mockMvc.perform(
                post("/api/documents/{documentId}/comments", documentId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "content": "Version-specific note",
                          "versionId": "%s"
                        }
                        """.formatted(versionId))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionNumber").value(1));

        mockMvc.perform(
                get("/api/documents/{documentId}", documentId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.comments.length()").value(2))
            .andExpect(jsonPath("$.data.comments[0].content").value("Version-specific note"))
            .andExpect(jsonPath("$.data.comments[1].content").value("Needs one final review before sharing."));
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "Comment Owner",
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
