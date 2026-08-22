package com.filemind.collaboration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:collaboration-flow;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/collaboration",
    "filemind.storage.uploads=target/test-storage/collaboration/uploads",
    "filemind.storage.chunks=target/test-storage/collaboration/chunks",
    "filemind.storage.manifests=target/test-storage/collaboration/manifests",
    "filemind.share.base-url=http://localhost:5173"
})
@AutoConfigureMockMvc
class CollaborationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void viewerIsReadOnlyEditorCanModifyAndPublicShareWorks() throws Exception {
        String ownerToken = registerAndLogin("owner-collab@example.com", "Owner User");
        String viewerToken = registerAndLogin("viewer@example.com", "Viewer User");
        String editorToken = registerAndLogin("editor@example.com", "Editor User");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "policy.txt",
            "text/plain",
            "This is the original collaboration document.".getBytes()
        );

        MvcResult upload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(file)
                    .header("Authorization", "Bearer " + ownerToken)
            )
            .andExpect(status().isOk())
            .andReturn();

        String documentId = objectMapper.readTree(upload.getResponse().getContentAsString())
            .path("data")
            .path("documentId")
            .asText();

        mockMvc.perform(
                post("/api/documents/{documentId}/collaborators", documentId)
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "viewer@example.com",
                          "permission": "VIEWER"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.permission").value("VIEWER"));

        mockMvc.perform(
                post("/api/documents/{documentId}/collaborators", documentId)
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "editor@example.com",
                          "permission": "EDITOR"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.permission").value("EDITOR"));

        MvcResult collaborators = mockMvc.perform(
                get("/api/documents/{documentId}/collaborators", documentId)
                    .header("Authorization", "Bearer " + ownerToken)
            )
            .andExpect(status().isOk())
            .andReturn();

        JsonNode collaboratorNodes = objectMapper.readTree(collaborators.getResponse().getContentAsString()).path("data");
        String editorAccessId = "";
        for (JsonNode collaboratorNode : collaboratorNodes) {
            if ("editor@example.com".equals(collaboratorNode.path("email").asText())) {
                editorAccessId = collaboratorNode.path("id").asText();
                break;
            }
        }
        assertThat(editorAccessId).isNotBlank();

        mockMvc.perform(
                patch("/api/documents/{documentId}/collaborators/{collaboratorId}", documentId, editorAccessId)
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "permission": "OWNER"
                        }
                        """)
            )
            .andExpect(status().isBadRequest());

        mockMvc.perform(
                get("/api/documents/{documentId}", documentId)
                    .header("Authorization", "Bearer " + viewerToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.permission").value("VIEWER"))
            .andExpect(jsonPath("$.data.canEdit").value(false));

        mockMvc.perform(
                patch("/api/documents/{documentId}/rename", documentId)
                    .header("Authorization", "Bearer " + viewerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "viewer-rename.txt"
                        }
                        """)
            )
            .andExpect(status().isForbidden());

        mockMvc.perform(
                patch("/api/documents/{documentId}/rename", documentId)
                    .header("Authorization", "Bearer " + editorToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "editor-updated.txt"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("editor-updated.txt"));

        MvcResult shareLinkResult = mockMvc.perform(
                post("/api/documents/{documentId}/share-link", documentId)
                    .header("Authorization", "Bearer " + ownerToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(true))
            .andReturn();

        JsonNode shareLinkBody = objectMapper.readTree(shareLinkResult.getResponse().getContentAsString());
        String token = shareLinkBody.path("data").path("token").asText();

        mockMvc.perform(get("/api/public/share/{token}", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("editor-updated.txt"));

        assertThat(shareLinkBody.path("data").path("url").asText()).contains("/shared/" + token);
    }

    @Test
    void ownerCanEnableShareLinkWithExpiry() throws Exception {
        String ownerToken = registerAndLogin("expiry-owner@example.com", "Owner User");

        MvcResult upload = mockMvc.perform(
                multipart("/api/documents/upload")
                    .file(new MockMultipartFile(
                        "file",
                        "expiring-share.txt",
                        "text/plain",
                        "Public links can now expire automatically.".getBytes()
                    ))
                    .header("Authorization", "Bearer " + ownerToken)
            )
            .andExpect(status().isOk())
            .andReturn();

        String documentId = objectMapper.readTree(upload.getResponse().getContentAsString())
            .path("data")
            .path("documentId")
            .asText();

        mockMvc.perform(
                post("/api/documents/{documentId}/share-link", documentId)
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "expiresInDays": 7
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(true))
            .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
    }

    private String registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "%s",
                          "email": "%s",
                          "password": "Password1"
                        }
                        """.formatted(fullName, email))
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
