package com.tfp.timetracking.shared.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.identity.interfaces.rest.AuthLoginRequest;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Rate limiting por endpoint (T30-03, RS-007).
 *
 * <p>Comprueba las tres propiedades que pide la ficha: los limites son
 * configurables por endpoint, el exceso responde 429 con Problem Details y
 * {@code errorCode: RATE_LIMIT_EXCEEDED}, y las cuotas de endpoints distintos
 * son independientes entre si.
 *
 * <p>Se apoya en rutas <b>que aun no existen</b> (recuperacion de contrasena y
 * reenvio de verificacion) a proposito: el filtro debe rechazar por limite
 * antes de que ninguna otra cosa mire la ruta, que es justo lo que permite que
 * esos endpoints nazcan ya protegidos cuando los publique su agente.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "auth.rate-limit.capacity=3",
            "auth.rate-limit.window=PT1M",
            "auth.rate-limit.endpoints[0].method=POST",
            "auth.rate-limit.endpoints[0].pattern=/api/v1/auth/login",
            "auth.rate-limit.endpoints[1].method=POST",
            "auth.rate-limit.endpoints[1].pattern=/api/v1/auth/refresh",
            "auth.rate-limit.endpoints[1].capacity=6",
            "auth.rate-limit.endpoints[2].method=POST",
            "auth.rate-limit.endpoints[2].pattern=/api/v1/auth/password/**",
            "auth.rate-limit.endpoints[2].capacity=2",
            "auth.rate-limit.endpoints[3].method=POST",
            "auth.rate-limit.endpoints[3].pattern=/api/v1/auth/verification/**",
            "auth.rate-limit.endpoints[3].capacity=2"
        })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitFilterIntegrationTest {

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
    void loginIsLimitedByTheGlobalCapacity() throws Exception {
        String ip = "198.18.0.1";

        loginAttempt(ip).andExpect(status().isUnauthorized());
        loginAttempt(ip).andExpect(status().isUnauthorized());
        loginAttempt(ip).andExpect(status().isUnauthorized());

        loginAttempt(ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void refreshIsAlsoLimitedButWithItsOwnHigherCapacity() throws Exception {
        String ip = "198.18.0.2";

        for (int attempt = 0; attempt < 6; attempt++) {
            refreshAttempt(ip).andExpect(status().isUnauthorized());
        }

        refreshAttempt(ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    /**
     * Cada regla tiene su propio bucket: agotar el de login no debe dejar sin
     * servicio al resto de endpoints desde la misma IP.
     */
    @Test
    void eachEndpointHasAnIndependentBudget() throws Exception {
        String ip = "198.18.0.3";

        for (int attempt = 0; attempt < 4; attempt++) {
            loginAttempt(ip);
        }
        loginAttempt(ip).andExpect(status().isTooManyRequests());

        refreshAttempt(ip).andExpect(status().isUnauthorized());
    }

    /** Los buckets se indexan por IP: un cliente no consume la cuota de otro. */
    @Test
    void theBudgetIsPerClientIp() throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            loginAttempt("198.18.0.4");
        }
        loginAttempt("198.18.0.4").andExpect(status().isTooManyRequests());

        loginAttempt("198.18.0.5").andExpect(status().isUnauthorized());
    }

    /**
     * Rutas todavia inexistentes: hoy responden 401 (no son publicas) o 404,
     * pero en cuanto se supera el limite el filtro corta con 429 antes de
     * llegar a la cadena de seguridad. Es la garantia de que naceran cubiertas.
     */
    @Test
    void plannedPasswordRecoveryRoutesAreCoveredByPattern() throws Exception {
        String ip = "198.18.0.6";

        emptyPost("/api/v1/auth/password/forgot", ip);
        emptyPost("/api/v1/auth/password/forgot", ip);

        emptyPost("/api/v1/auth/password/reset", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void plannedVerificationResendRouteIsCoveredByPattern() throws Exception {
        String ip = "198.18.0.7";

        emptyPost("/api/v1/auth/verification/resend", ip);
        emptyPost("/api/v1/auth/verification/resend", ip);

        emptyPost("/api/v1/auth/verification/resend", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    /** Un endpoint fuera de las reglas no consume ni impone cuota. */
    @Test
    void endpointsOutsideTheRulesAreNotLimited() throws Exception {
        String ip = "198.18.0.8";

        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(post("/api/v1/auth/logout").header("X-Forwarded-For", ip))
                    .andExpect(status().isUnauthorized());
        }
    }

    private ResultActions loginAttempt(String clientIp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AuthLoginRequest("nobody+" + UUID.randomUUID() + "@acme.test", "wrong-password"))));
    }

    private ResultActions refreshAttempt(String clientIp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .header("X-Forwarded-For", clientIp)
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "not-a-valid-token")));
    }

    private ResultActions emptyPost(String path, String clientIp) throws Exception {
        return mockMvc.perform(post(path)
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }
}
