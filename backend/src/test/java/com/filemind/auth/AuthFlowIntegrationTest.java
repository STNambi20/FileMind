package com.filemind.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:auth-flow;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/auth",
    "filemind.storage.uploads=target/test-storage/auth/uploads",
    "filemind.storage.chunks=target/test-storage/auth/chunks",
    "filemind.storage.manifests=target/test-storage/auth/manifests"
})
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginAndFetchCurrentUser() throws Exception {
        String registerPayload = """
            {
              "fullName": "Gautam Kewat",
              "email": "gautam@example.com",
              "password": "Password1"
            }
            """;

        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerPayload)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.email").value("gautam@example.com"));

        MvcResult loginResult = mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "gautam@example.com",
                          "password": "Password1"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andReturn();

        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = body.path("data").path("accessToken").asText();

        mockMvc.perform(
                get("/api/auth/me")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fullName").value("Gautam Kewat"))
            .andExpect(jsonPath("$.data.email").value("gautam@example.com"));
    }
}

