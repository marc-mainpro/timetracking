package com.tfp.timetracking.identity.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.tenant.interfaces.rest.RegisterTenantRequest;
import com.tfp.timetracking.tenant.interfaces.rest.RegisterTenantResponse;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PasswordResetControllerIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void forgotReturnsAcceptedWithoutLeakingWhetherTheEmailExists() throws Exception {
        RegisteredAdmin admin = registerAdmin();

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", admin.email()))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").isString());

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "ghost@example.com"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void forgotPersistsHashedTokenAndEnqueuesOutboxEvent() throws Exception {
        RegisteredAdmin admin = registerAdmin();

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", admin.email()))))
                .andExpect(status().isAccepted());

        Integer tokens = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM password_reset_token WHERE user_id = ?", Integer.class, admin.userId());
        Integer outbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_message WHERE event_type = 'identity.password-reset-requested.v1'", Integer.class);
        assertThat(tokens).isEqualTo(1);
        assertThat(outbox).isEqualTo(1);
    }

    @Test
    void resetChangesPasswordAndRevokesRefreshTokens() throws Exception {
        RegisteredAdmin admin = registerAdmin();
        LoginResult login = login(admin);
        String rawToken = requestResetToken(admin.email());

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(cookie(login.cookie()))
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawToken,
                                "newPassword", "new-supersecretpwd"))))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie(login.cookie())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("SESSION_INACTIVE"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", admin.email(),
                                "password", "new-supersecretpwd"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    private String requestResetToken(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted());
        return jdbcTemplate.queryForObject(
                "SELECT payload ->> 'resetToken' FROM outbox_message WHERE event_type = 'identity.password-reset-requested.v1' ORDER BY created_at DESC LIMIT 1",
                String.class);
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

    private LoginResult login(RegisteredAdmin admin) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", admin.email(), "password", admin.password()))))
                .andExpect(status().isOk())
                .andReturn();
        AuthTokenResponse body = objectMapper.readValue(result.getResponse().getContentAsString(), AuthTokenResponse.class);
        return new LoginResult(body.accessToken(), extractCookie(result));
    }

    private String extractCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private String cookieValue(String cookie) {
        return cookie.substring(cookie.indexOf('=') + 1);
    }

    private Cookie cookie(String cookiePair) {
        return new Cookie("refresh_token", cookieValue(cookiePair));
    }

    private record RegisteredAdmin(UUID tenantId, UUID userId, String email, String password) {}

    private record LoginResult(String accessToken, String cookie) {}
}
