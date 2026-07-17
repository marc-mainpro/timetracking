package com.tfp.timetracking.tenant.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.identity.domain.Role;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de integracion (Testcontainers PostgreSQL + MockMvc) de
 * {@code POST /api/v1/auth/register} (ficha T203): 201 feliz (persiste tenant
 * + admin) y 400 de validacion con detalle por campo (CONTEXT-GLOBAL §7).
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthRegisterControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registersTenantAndAdminReturns201WithIds() throws Exception {
        RegisterTenantRequest request = new RegisterTenantRequest(
                "Acme Corp " + Instant.now().toEpochMilli(),
                "Europe/Madrid",
                "admin+" + Instant.now().toEpochMilli() + "@acme.test",
                "supersecretpwd",
                "Jane",
                "Doe");

        String responseBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").exists())
                .andExpect(jsonPath("$.adminUserId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        RegisterTenantResponse response = objectMapper.readValue(responseBody, RegisterTenantResponse.class);

        Long tenantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant WHERE id = ?", Long.class, response.tenantId());
        assertThat(tenantCount).isEqualTo(1L);

        Long adminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE id = ? AND tenant_id = ?",
                Long.class,
                response.adminUserId(),
                response.tenantId());
        assertThat(adminCount).isEqualTo(1L);

        java.util.List<String> roles = jdbcTemplate.queryForList(
                "SELECT role FROM user_role WHERE user_id = ?", String.class, response.adminUserId());
        assertThat(roles).containsExactly(Role.TENANT_ADMIN.name());
    }

    @Test
    void rejectsRequestWithFieldValidationErrors() throws Exception {
        RegisterTenantRequest invalidRequest =
                new RegisterTenantRequest("", "Europe/Madrid", "not-an-email", "short", "", "Doe");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[*].field", hasItem("tenantName")));
    }

    @Test
    void rejectsInvalidTimezoneAsBadRequest() throws Exception {
        RegisterTenantRequest request = new RegisterTenantRequest(
                "Acme Corp " + Instant.now().toEpochMilli(),
                "Not/AZone",
                "admin2+" + Instant.now().toEpochMilli() + "@acme.test",
                "supersecretpwd",
                "Jane",
                "Doe");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"));
    }
}
