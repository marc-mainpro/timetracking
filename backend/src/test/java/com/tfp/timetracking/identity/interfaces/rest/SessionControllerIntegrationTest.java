package com.tfp.timetracking.identity.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.tenant.interfaces.rest.RegisterTenantRequest;
import com.tfp.timetracking.tenant.interfaces.rest.RegisterTenantResponse;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
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
class SessionControllerIntegrationTest {

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
    void listsCurrentUserSessionsAndMarksCurrentOne() throws Exception {
        RegisteredAdmin admin = registerAdmin();
        LoginResult login = login(admin);

        MvcResult result = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].current").value(true))
                .andReturn();

        List<SessionResponse> sessions = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(sessions).hasSize(1);
        assertThat(sessions.getFirst().id()).isNotNull();
    }

    @Test
    void revokingCurrentSessionClearsCookieAndBlocksFurtherUseOfAccessToken() throws Exception {
        RegisteredAdmin admin = registerAdmin();
        LoginResult login = login(admin);
        SessionResponse currentSession = currentSession(login.accessToken());

        mockMvc.perform(delete("/api/v1/auth/sessions/{sessionId}", currentSession.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mockMvc.perform(get("/api/v1/auth/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("SESSION_INACTIVE"));
    }

    @Test
    void revokingAllSessionsRevokesCurrentRefreshChain() throws Exception {
        RegisteredAdmin admin = registerAdmin();
        LoginResult login = login(admin);

        mockMvc.perform(delete("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                        .cookie(cookie(login.cookie())))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        Integer activeSessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class,
                admin.userId());
        Integer activeTokens = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class,
                admin.userId());
        assertThat(activeSessions).isZero();
        assertThat(activeTokens).isZero();
    }

    private SessionResponse currentSession(String accessToken) throws Exception {
        String response = mockMvc.perform(get("/api/v1/auth/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(response, new TypeReference<List<SessionResponse>>() {}).getFirst();
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
                        .content(objectMapper.writeValueAsString(new AuthLoginRequest(admin.email(), admin.password()))))
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
