package com.filemind.admin;

import com.filemind.auth.security.JwtService;
import com.filemind.user.entity.User;
import com.filemind.user.entity.UserRole;
import com.filemind.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:admin-access;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/admin-access",
    "filemind.storage.uploads=target/test-storage/admin-access/uploads",
    "filemind.storage.chunks=target/test-storage/admin-access/chunks",
    "filemind.storage.manifests=target/test-storage/admin-access/manifests"
})
@AutoConfigureMockMvc
class AdminAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void nonAdminUserIsForbiddenFromAdminOverview() throws Exception {
        User user = new User();
        user.setFullName("Basic User");
        user.setEmail("basic@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setRole(UserRole.USER);
        userRepository.save(user);

        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/api/admin/overview")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Access denied."));
    }

    @Test
    void adminUserCanViewAdminOverview() throws Exception {
        User user = new User();
        user.setFullName("Admin User");
        user.setEmail("admin@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);

        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/api/admin/overview")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalUsers").exists());
    }

    @Test
    void bootstrapAdminCreatesFirstAdminWhenNoAdminsExist() throws Exception {
        mockMvc.perform(post("/api/public/bootstrap-admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "fullName": "System Admin",
                      "email": "system-admin@example.com",
                      "password": "Password1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
            .andExpect(jsonPath("$.data.user.email").value("system-admin@example.com"));
    }
}
