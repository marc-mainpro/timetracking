package com.tfp.timetracking.identity.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.shared.infrastructure.security.CorrelationIdFilter;
import com.tfp.timetracking.tenant.interfaces.rest.RegisterTenantRequest;
import com.tfp.timetracking.tenant.interfaces.rest.RegisterTenantResponse;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(properties = {"auth.rate-limit.capacity=2", "auth.rate-limit.window=PT1S"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthSecurityIntegrationTest {

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

    @Test
    void anonymousUserGets401OnPrivateEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void invalidBearerGets401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredJwt()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void inactiveUserCannotLogin() throws Exception {
        RegisteredAdmin admin = registerAdmin(ip("inactive-user-register"));
        jdbcTemplate.update("UPDATE app_user SET status = 'INACTIVE' WHERE id = ?", admin.userId());

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip("inactive-user-login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthLoginRequest(admin.email(), admin.password()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("USER_INACTIVE"));
    }

    @Test
    void deactivatedUserWithValidTokenGets401OnAuthenticatedRequest() throws Exception {
        RegisteredAdmin admin = registerAdmin(ip("token-user-register"));
        LoginResult login = login(admin, ip("token-user-login"));
        jdbcTemplate.update("UPDATE app_user SET status = 'INACTIVE' WHERE id = ?", admin.userId());

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("USER_INACTIVE"));
    }

    @Test
    void inactiveTenantWithValidTokenGets401OnAuthenticatedRequest() throws Exception {
        RegisteredAdmin admin = registerAdmin(ip("token-tenant-register"));
        LoginResult login = login(admin, ip("token-tenant-login"));
        jdbcTemplate.update("UPDATE tenant SET status = 'INACTIVE' WHERE id = ?", admin.tenantId());

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TENANT_INACTIVE"));
    }

    @Test
    void deactivatedUserCannotRefreshSession() throws Exception {
        RegisteredAdmin admin = registerAdmin(ip("refresh-deactivated-register"));
        LoginResult login = login(admin, ip("refresh-deactivated-login"));
        jdbcTemplate.update("UPDATE app_user SET status = 'INACTIVE' WHERE id = ?", admin.userId());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie(login.cookie())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("USER_INACTIVE"));
    }

    @Test
    void reusedRefreshTokenInvalidatesActiveChain() throws Exception {
        RegisteredAdmin admin = registerAdmin(ip("reuse-register"));
        LoginResult login = login(admin, ip("reuse-login"));
        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie(login.cookie())))
                .andExpect(status().isOk())
                .andReturn();

        String rotatedCookie = extractCookie(rotated);

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie(login.cookie())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REFRESH_TOKEN_REUSED"));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie(rotatedCookie)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REFRESH_TOKEN_REUSED"));

        Integer activeTokens = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL", Integer.class, admin.userId());
        assertThat(activeTokens).isZero();
    }

    @Test
    void loginIsRateLimitedAndAllowsAgainAfterWindow() throws Exception {
        RegisteredAdmin admin = registerAdmin(ip("limit-register"));
        String ip = ip("limit-login");

        loginRequest(admin, ip).andExpect(status().isOk());
        loginRequest(admin, ip).andExpect(status().isOk());
        loginRequest(admin, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));

        Thread.sleep(1100L);

        loginRequest(admin, ip).andExpect(status().isOk());
    }

    @Test
    void registerIsRateLimited() throws Exception {
        String ip = ip("limit-register-only");

        register(ip, 1).result().andExpect(status().isCreated());
        register(ip, 2).result().andExpect(status().isCreated());
        register(ip, 3).result()
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void authResponsesCarrySecurityHeaders() throws Exception {
        RegisteredAdmin admin = registerAdmin(ip("headers-register"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip("headers-login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthLoginRequest(admin.email(), admin.password()))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"));

        register(ip("headers-register-public"), 4).result()
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"));
    }

    @Test
    void corsOnlyAllowsConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.VARY, containsString("Origin")));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void registerDoesNotRevealWhetherEmailAlreadyExists() throws Exception {
        String clientIp = ip("register-enum");
        RegisterAttempt first = register(clientIp, 10);
        first.result().andExpect(status().isCreated());

        RegisterTenantRequest duplicateRequest = new RegisterTenantRequest(
                "Acme Duplicate",
                "Europe/Madrid",
                first.email(),
                "supersecretpwd",
                "Jane",
                "Doe");

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", ip("register-enum-2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_IN_USE"))
                .andExpect(jsonPath("$.detail", not(containsString(first.email()))));
    }

    @Test
    void oversizedPayloadIsRejected() throws Exception {
        String hugeTenantName = "A".repeat(70_000);
        RegisterTenantRequest request = new RegisterTenantRequest(
                hugeTenantName,
                "Europe/Madrid",
                "oversized@acme.test",
                "supersecretpwd",
                "Jane",
                "Doe");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void correlationIdIsPropagatedIntoProblemDetails() throws Exception {
        String correlationId = "corr-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId))
                .andExpect(jsonPath("$.correlationId").value(correlationId));
    }

    private RegisteredAdmin registerAdmin(String clientIp) throws Exception {
        RegisterAttempt attempt = register(clientIp, 0);
        String responseBody = attempt.result().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        RegisterTenantResponse response = objectMapper.readValue(responseBody, RegisterTenantResponse.class);
        return new RegisteredAdmin(response.tenantId(), response.adminUserId(), attempt.email(), "supersecretpwd");
    }

    private RegisterAttempt register(String clientIp, int offset) throws Exception {
        long suffix = Instant.now().toEpochMilli() + offset;
        String email = "admin+" + suffix + "@acme.test";
        RegisterTenantRequest request = new RegisterTenantRequest(
                "Acme Corp " + suffix,
                "Europe/Madrid",
                email,
                "supersecretpwd",
                "Jane",
                "Doe");
        return new RegisterAttempt(email, mockMvc.perform(post("/api/v1/auth/register")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))));
    }

    private LoginResult login(RegisteredAdmin admin, String clientIp) throws Exception {
        MvcResult result = loginRequest(admin, clientIp).andExpect(status().isOk()).andReturn();
        AuthTokenResponse body = objectMapper.readValue(result.getResponse().getContentAsString(), AuthTokenResponse.class);
        return new LoginResult(body.accessToken(), extractCookie(result));
    }

    private org.springframework.test.web.servlet.ResultActions loginRequest(RegisteredAdmin admin, String clientIp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthLoginRequest(admin.email(), admin.password()))));
    }

    private String extractCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private Cookie cookie(String cookiePair) {
        return new Cookie("refresh_token", cookiePair.substring(cookiePair.indexOf('=') + 1));
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

    private String ip(String seed) {
        int octet = Math.abs(seed.hashCode() % 200) + 20;
        return "203.0.113." + octet;
    }

    private record RegisteredAdmin(UUID tenantId, UUID userId, String email, String password) {}

    private record LoginResult(String accessToken, String cookie) {}

    private record RegisterAttempt(String email, org.springframework.test.web.servlet.ResultActions result) {}
}
