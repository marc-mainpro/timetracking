package com.tfp.timetracking.tenant.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Con el registro público deshabilitado (comportamiento por defecto en
 * producción, RF-TEN-010), tanto el alta heredada {@code /api/v1/auth/register}
 * como el flujo de solicitudes de la V2 responden 403: los tenants solo se
 * crean desde la administración de plataforma.
 *
 * <p>La bandera se comprueba en las tres operaciones públicas, no solo en el
 * alta: si solo se cerrase la puerta de entrada, seguir verificando o reenviando
 * correos de solicitudes previas mantendría vivo medio flujo.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "registration.public.enabled=false")
class PublicRegistrationDisabledIntegrationTest {

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
    void publicRegistrationIsForbiddenWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", "203.0.113.200")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterTenantRequest(
                                "Bloqueada", "Europe/Madrid", "blocked@acme.test", "supersecretpwd", "A", "B"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantRegistrationRequestsAreForbiddenWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/public/tenant-registrations")
                        .header("X-Forwarded-For", "203.0.113.201")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TenantRegistrationRequestBody(
                                "Bloqueada",
                                "Europe/Madrid",
                                "Ana",
                                "Ruiz",
                                "blocked-v2@acme.test",
                                "supersecretpwd",
                                true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void verificationAndResendAreAlsoForbiddenWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/public/tenant-registrations/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationEmailRequest("cualquiera"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/public/tenant-registrations/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResendVerificationRequest("alguien@acme.test"))))
                .andExpect(status().isForbidden());
    }
}
