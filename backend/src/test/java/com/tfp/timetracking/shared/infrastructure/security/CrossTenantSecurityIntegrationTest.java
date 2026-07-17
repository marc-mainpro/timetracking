package com.tfp.timetracking.shared.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.application.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(properties = {"auth.rate-limit.capacity=20", "auth.rate-limit.window=PT1M"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CrossTenantSecurityIntegrationTest {

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
    private TestTenantFactory testTenantFactory;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void adminOfTenantACannotReadOrMutateUsersOfTenantB() throws Exception {
        TestTenantFactory.TenantActors tenantA = testTenantFactory.createTenantActors("A");
        TestTenantFactory.TenantActors tenantB = testTenantFactory.createTenantActors("B");

        mockMvc.perform(get("/api/v1/test/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].userId").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(tenantB.admin().userId().toString()))));

        mockMvc.perform(get("/api/v1/test/admin/users/{userId}", tenantB.admin().userId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA.admin().token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/test/admin/users/{userId}/touch", tenantB.employee().userId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA.admin().token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void forgedTokenWithForeignTenantClaimIsRejected() throws Exception {
        TestTenantFactory.TenantActors tenantA = testTenantFactory.createTenantActors("A-forged");
        TestTenantFactory.TenantActors tenantB = testTenantFactory.createTenantActors("B-forged");

        String tamperedToken = forgedToken(tenantA.admin().userId(), tenantB.tenantId(), List.of("TENANT_ADMIN"));

        mockMvc.perform(get("/api/v1/test/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void tenantIsolationRemainsWhenOtherTenantGetsDeactivated() throws Exception {
        TestTenantFactory.TenantActors tenantA = testTenantFactory.createTenantActors("A-active");
        TestTenantFactory.TenantActors tenantB = testTenantFactory.createTenantActors("B-inactive");

        jdbcTemplate.update("UPDATE tenant SET status = 'INACTIVE' WHERE id = ?", tenantB.tenantId());

        mockMvc.perform(get("/api/v1/test/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA.admin().token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/test/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantB.admin().token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TENANT_INACTIVE"));
    }

    @Test
    void employeeCannotAccessAdminOnlyEndpoint() throws Exception {
        TestTenantFactory.TenantActors tenantA = testTenantFactory.createTenantActors("A-employee");

        mockMvc.perform(get("/api/v1/test/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA.employee().token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOfTenantACannotReadWorkdayOfTenantB() throws Exception {
        TestTenantFactory.TenantActors tenantA = testTenantFactory.createTenantActors("A-workday");
        TestTenantFactory.TenantActors tenantB = testTenantFactory.createTenantActors("B-workday");

        String workdayId = startWorkday(tenantB.employee().token());

        mockMvc.perform(get("/api/v1/admin/workdays/{workdayId}", workdayId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA.admin().token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminOfTenantAListsOnlyOwnTenantWorkdays() throws Exception {
        TestTenantFactory.TenantActors tenantA = testTenantFactory.createTenantActors("A-admin-list");
        TestTenantFactory.TenantActors tenantB = testTenantFactory.createTenantActors("B-admin-list");
        String workdayA = startWorkday(tenantA.employee().token());
        String workdayB = startWorkday(tenantB.employee().token());

        mockMvc.perform(get("/api/v1/admin/workdays")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(workdayA));
    }

    @Test
    void adminOfTenantACannotManageEmployeesOfTenantB() throws Exception {
        TestTenantFactory.TenantActors tenantA = testTenantFactory.createTenantActors("A-employee-admin");
        TestTenantFactory.TenantActors tenantB = testTenantFactory.createTenantActors("B-employee-admin");

        mockMvc.perform(get("/api/v1/employees/{employeeId}", tenantB.employee().userId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA.admin().token()))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration
    static class CrossTenantTestConfiguration {

        @Bean
        TestTenantFactory testTenantFactory(
                MockMvc mockMvc,
                com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                UserRepository userRepository,
                com.tfp.timetracking.identity.domain.PasswordHasher passwordHasher,
                com.tfp.timetracking.shared.domain.Clock clock,
                com.tfp.timetracking.shared.domain.IdGenerator idGenerator) {
            return new TestTenantFactory(mockMvc, objectMapper, userRepository, passwordHasher, clock, idGenerator);
        }

        @Bean
        CrossTenantAdminProbeController crossTenantAdminProbeController(TenantContext tenantContext, UserRepository userRepository) {
            return new CrossTenantAdminProbeController(tenantContext, userRepository);
        }
    }

    @RestController
    @RequestMapping("/api/v1/test/admin/users")
    static class CrossTenantAdminProbeController {

        private final TenantContext tenantContext;
        private final UserRepository userRepository;

        CrossTenantAdminProbeController(TenantContext tenantContext, UserRepository userRepository) {
            this.tenantContext = tenantContext;
            this.userRepository = userRepository;
        }

        @GetMapping
        @PreAuthorize("hasRole('TENANT_ADMIN')")
        List<Map<String, String>> listCurrentTenantUsers() {
            return userRepository.findAllByTenantId(tenantContext.currentTenantId()).stream()
                    .map(user -> Map.of("userId", user.id().toString(), "email", user.email().value()))
                    .toList();
        }

        @GetMapping("/{userId}")
        @PreAuthorize("hasRole('TENANT_ADMIN')")
        ResponseEntity<Map<String, String>> getTenantUser(@PathVariable UUID userId) {
            return userRepository.findById(tenantContext.currentTenantId(), userId)
                    .map(user -> ResponseEntity.ok(Map.of("userId", user.id().toString(), "email", user.email().value())))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        @PostMapping("/{userId}/touch")
        @PreAuthorize("hasRole('TENANT_ADMIN')")
        ResponseEntity<Void> touchTenantUser(@PathVariable UUID userId) {
            return userRepository.findById(tenantContext.currentTenantId(), userId)
                    .map(user -> ResponseEntity.noContent().<Void>build())
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }
    }

    private String forgedToken(UUID userId, UUID tenantId, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .claim("tenantId", tenantId.toString())
                .claim("roles", roles)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String startWorkday(String token) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(mockMvc.perform(post("/api/v1/workdays/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();
    }
}
