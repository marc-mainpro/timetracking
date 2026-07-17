package com.tfp.timetracking.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.identity.interfaces.rest.AuthLoginRequest;
import com.tfp.timetracking.identity.interfaces.rest.AuthTokenResponse;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.tenant.interfaces.rest.RegisterTenantRequest;
import com.tfp.timetracking.tenant.interfaces.rest.RegisterTenantResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtTenantContextIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("timetracking")
            .withUsername("timetracking")
            .withPassword("timetracking");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void resolvesTenantContextFromAuthenticatedRequest() throws Exception {
        RegisteredAdmin admin = registerAdmin();
        AuthTokenResponse login = login(admin);

        mockMvc.perform(get("/api/v1/test/tenant-context")
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(admin.tenantId().toString()))
                .andExpect(jsonPath("$.userId").value(admin.userId().toString()))
                .andExpect(jsonPath("$.roles[0]").value("TENANT_ADMIN"));
    }

    private RegisteredAdmin registerAdmin() throws Exception {
        long suffix = Instant.now().toEpochMilli();
        String email = "admin+" + suffix + "@acme.test";
        String password = "supersecretpwd";
        RegisterTenantRequest request =
                new RegisterTenantRequest("Acme Corp " + suffix, "Europe/Madrid", email, password, "Jane", "Doe");
        String responseBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        RegisterTenantResponse response = objectMapper.readValue(responseBody, RegisterTenantResponse.class);
        return new RegisteredAdmin(response.tenantId(), response.adminUserId(), email, password);
    }

    private AuthTokenResponse login(RegisteredAdmin admin) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthLoginRequest(admin.email(), admin.password()))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(response, AuthTokenResponse.class);
    }

    private record RegisteredAdmin(UUID tenantId, UUID userId, String email, String password) {}

    @TestConfiguration
    static class TenantContextTestConfiguration {

        @Bean
        TenantContextProbeController tenantContextProbeController(TenantContext tenantContext) {
            return new TenantContextProbeController(tenantContext);
        }
    }

    @RestController
    static class TenantContextProbeController {

        private final TenantContext tenantContext;

        TenantContextProbeController(TenantContext tenantContext) {
            this.tenantContext = tenantContext;
        }

        @GetMapping("/api/v1/test/tenant-context")
        Map<String, Object> current() {
            return Map.of(
                    "tenantId", tenantContext.currentTenantId(),
                    "userId", tenantContext.currentUserId(),
                    "roles", tenantContext.currentRoles());
        }
    }
}
