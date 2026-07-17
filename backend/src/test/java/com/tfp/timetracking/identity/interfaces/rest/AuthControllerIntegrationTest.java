package com.tfp.timetracking.identity.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.tenant.interfaces.rest.RegisterTenantRequest;
import com.tfp.timetracking.tenant.interfaces.rest.RegisterTenantResponse;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
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
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIntegrationTest {

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

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void loginReturnsAccessTokenAndRefreshCookie() throws Exception {
        RegisteredAdmin admin = registerAdmin();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthLoginRequest(admin.email(), admin.password()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andReturn();

        AuthTokenResponse body = objectMapper.readValue(result.getResponse().getContentAsString(), AuthTokenResponse.class);
        org.springframework.security.oauth2.jwt.Jwt jwt = jwtDecoder.decode(body.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(admin.userId().toString());
        assertThat(jwt.getClaimAsString("tenantId")).isEqualTo(admin.tenantId().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("TENANT_ADMIN");
    }

    @Test
    void refreshRotatesCookieAndIssuesNewAccessToken() throws Exception {
        RegisteredAdmin admin = registerAdmin();
        LoginResult login = login(admin);

        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie(login.cookie())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();

        String rotatedCookie = extractCookie(refresh);
        assertThat(rotatedCookie).isNotEqualTo(login.cookie());
        Integer activeTokens = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class,
                admin.userId());
        assertThat(activeTokens).isEqualTo(1);
    }

    @Test
    void reusedRefreshTokenReturns401AndInvalidatesChain() throws Exception {
        RegisteredAdmin admin = registerAdmin();
        LoginResult login = login(admin);
        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie(login.cookie())))
                .andExpect(status().isOk())
                .andReturn();
        String rotatedCookie = extractCookie(refresh);

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie(login.cookie())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REFRESH_TOKEN_REUSED"));

        Integer activeTokens = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class,
                admin.userId());
        assertThat(activeTokens).isZero();
        assertThat(rotatedCookie).contains("refresh_token=");
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
        RegisteredAdmin admin = registerAdmin();
        LoginResult login = login(admin);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                        .cookie(cookie(login.cookie())))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        Integer activeTokens = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class,
                admin.userId());
        assertThat(activeTokens).isZero();
    }

    @Test
    void invalidOrExpiredBearerTokenGets401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredJwt()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void anonymousCannotAccessProtectedLogoutEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void loginDoesNotLeakSecretsInLogs(CapturedOutput output) throws Exception {
        RegisteredAdmin admin = registerAdmin();
        LoginResult login = login(admin);

        assertThat(output.getOut()).doesNotContain(admin.password());
        assertThat(output.getOut()).doesNotContain(login.accessToken());
        assertThat(output.getOut()).doesNotContain(cookieValue(login.cookie()));
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

    private String expiredJwt() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(now.minusSeconds(120))
                .expiresAt(now.minusSeconds(60))
                .claim("tenantId", UUID.randomUUID().toString())
                .claim("roles", java.util.List.of("TENANT_ADMIN"))
                .build();
        return jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
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
