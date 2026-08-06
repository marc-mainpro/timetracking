package com.tfp.timetracking.identity.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.identity.application.AccountLockoutService;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Bloqueo temporal de cuentas extremo a extremo (T30-04, RF-USR-008, RS-008).
 *
 * <p>El umbral se baja a 3 y el rate limit se sube, porque aqui interesa
 * comprobar el bloqueo por cuenta y no el limite por IP: son dos controles
 * distintos y el test de cada uno no debe dispararse por el otro.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "auth.account-lockout.threshold=3",
            "auth.account-lockout.lock-duration=PT2S",
            "auth.account-lockout.failure-window=PT30M",
            "auth.rate-limit.capacity=100",
            "auth.rate-limit.window=PT1M"
        })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountLockoutIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private Clock clock;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private RegisterTenantUseCase registerTenantUseCase;

    private TestTenantFactory tenantFactory;

    @BeforeEach
    void setUp() {
        tenantFactory = new TestTenantFactory(
                    mockMvc, objectMapper, registerTenantUseCase, userRepository, passwordHasher, clock, idGenerator);
    }

    @Test
    void locksTheAccountAfterTheConfiguredNumberOfFailedAttempts() throws Exception {
        TestTenantFactory.TenantActors actors = tenantFactory.createTenantActors("lockout");
        String email = actors.employee().email();
        String ip = "192.0.2.10";

        login(email, "wrong-password", ip).andExpect(status().isUnauthorized());
        login(email, "wrong-password", ip).andExpect(status().isUnauthorized());
        login(email, "wrong-password", ip).andExpect(status().isUnauthorized());

        // Con la contrasena correcta si se distingue el bloqueo: quien la
        // conoce es el titular de la cuenta y necesita saber que le pasa.
        login(email, actors.employee().password(), ip)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));

        assertThat(lockedUntil(actors.employee().userId())).isNotNull();
        assertThat(auditActions(actors.employee().userId())).contains("LOGIN_FAILED", "ACCOUNT_LOCKED");
    }

    /**
     * RS-008 anti-enumeracion: contra credenciales incorrectas, una cuenta
     * bloqueada, una cuenta que existe y un email inventado responden
     * exactamente lo mismo. Si no fuera asi, el propio bloqueo se convertiria
     * en un oraculo para enumerar usuarios validos.
     */
    @Test
    void lockedAccountIsIndistinguishableFromAnUnknownAccount() throws Exception {
        TestTenantFactory.TenantActors actors = tenantFactory.createTenantActors("lockout-enum");
        String email = actors.employee().email();
        String ip = "192.0.2.11";

        for (int attempt = 0; attempt < 3; attempt++) {
            login(email, "wrong-password", ip).andExpect(status().isUnauthorized());
        }
        assertThat(lockedUntil(actors.employee().userId())).isNotNull();

        String lockedBody = login(email, "another-wrong-password", ip)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String unknownBody = login("nobody+" + UUID.randomUUID() + "@acme.test", "another-wrong-password", ip)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(withoutVolatileFields(lockedBody)).isEqualTo(withoutVolatileFields(unknownBody));
    }

    @Test
    void theAccountUnlocksItselfWhenTheLockExpires() throws Exception {
        TestTenantFactory.TenantActors actors = tenantFactory.createTenantActors("lockout-expiry");
        String email = actors.employee().email();
        String ip = "192.0.2.12";

        for (int attempt = 0; attempt < 3; attempt++) {
            login(email, "wrong-password", ip).andExpect(status().isUnauthorized());
        }
        login(email, actors.employee().password(), ip)
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));

        Thread.sleep(2100L);

        login(email, actors.employee().password(), ip).andExpect(status().isOk());
    }

    @Test
    void aSuccessfulLoginResetsTheFailureCounter() throws Exception {
        TestTenantFactory.TenantActors actors = tenantFactory.createTenantActors("lockout-reset");
        String email = actors.employee().email();
        String ip = "192.0.2.13";

        login(email, "wrong-password", ip).andExpect(status().isUnauthorized());
        login(email, "wrong-password", ip).andExpect(status().isUnauthorized());
        assertThat(failedAttempts(actors.employee().userId())).isEqualTo(2);

        login(email, actors.employee().password(), ip).andExpect(status().isOk());
        assertThat(failedAttempts(actors.employee().userId())).isZero();

        // Con el contador reiniciado hacen falta otros 3 fallos para bloquear.
        login(email, "wrong-password", ip).andExpect(status().isUnauthorized());
        login(email, "wrong-password", ip).andExpect(status().isUnauthorized());
        login(email, actors.employee().password(), ip).andExpect(status().isOk());
    }

    /** RT-003: el bloqueo de un tenant no afecta a un usuario homonimo de otro. */
    @Test
    void lockingAnAccountDoesNotAffectAccountsOfAnotherTenant() throws Exception {
        TestTenantFactory.TenantActors victim = tenantFactory.createTenantActors("lockout-cross-a");
        TestTenantFactory.TenantActors bystander = tenantFactory.createTenantActors("lockout-cross-b");

        for (int attempt = 0; attempt < 3; attempt++) {
            login(victim.employee().email(), "wrong-password", "192.0.2.14").andExpect(status().isUnauthorized());
        }

        assertThat(lockedUntil(victim.employee().userId())).isNotNull();
        assertThat(lockedUntil(bystander.employee().userId())).isNull();
        login(bystander.employee().email(), bystander.employee().password(), "192.0.2.15")
                .andExpect(status().isOk());
    }

    /** RT-004: el bloqueo se aplica igual al administrador que al empleado. */
    @Test
    void lockoutAppliesToTenantAdminsToo() throws Exception {
        TestTenantFactory.TenantActors actors = tenantFactory.createTenantActors("lockout-role");

        for (int attempt = 0; attempt < 3; attempt++) {
            login(actors.admin().email(), "wrong-password", "192.0.2.16").andExpect(status().isUnauthorized());
        }

        login(actors.admin().email(), actors.admin().password(), "192.0.2.16")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
        assertThat(lockedUntil(actors.admin().userId())).isNotNull();
    }

    @Test
    void failedAndLockedCountersAreExposedAsMetrics() throws Exception {
        TestTenantFactory.TenantActors actors = tenantFactory.createTenantActors("lockout-metrics");
        double failedBefore = counter("auth.login.failed", "bad_credentials");
        double lockedBefore = counter("auth.accounts.locked", null);

        for (int attempt = 0; attempt < 3; attempt++) {
            login(actors.employee().email(), "wrong-password", "192.0.2.17").andExpect(status().isUnauthorized());
        }

        assertThat(counter("auth.login.failed", "bad_credentials")).isEqualTo(failedBefore + 3);
        assertThat(counter("auth.accounts.locked", null)).isEqualTo(lockedBefore + 1);
    }

    @Test
    void theLockoutRowIsScopedToTheTenantOfItsUser() throws Exception {
        TestTenantFactory.TenantActors actors = tenantFactory.createTenantActors("lockout-scope");
        login(actors.employee().email(), "wrong-password", "192.0.2.18").andExpect(status().isUnauthorized());

        UUID storedTenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM account_lockout WHERE user_id = ?", UUID.class, actors.employee().userId());

        assertThat(storedTenantId).isEqualTo(actors.tenantId());
    }

    @Test
    void auditOfAFailedLoginIsRecordedUnderTheTenantOfTheAccount() throws Exception {
        TestTenantFactory.TenantActors actors = tenantFactory.createTenantActors("lockout-audit");
        login(actors.employee().email(), "wrong-password", "192.0.2.19").andExpect(status().isUnauthorized());

        Map<String, Object> auditRow = jdbcTemplate.queryForMap(
                "SELECT tenant_id, actor_user_id, action, entity_type FROM audit_event"
                        + " WHERE entity_id = ? AND action = ?",
                actors.employee().userId(),
                AccountLockoutService.AUDIT_LOGIN_FAILED);

        assertThat(auditRow.get("tenant_id")).isEqualTo(actors.tenantId());
        assertThat(auditRow.get("actor_user_id")).isEqualTo(actors.employee().userId());
        assertThat(auditRow.get("entity_type")).isEqualTo("User");
    }

    private ResultActions login(String email, String password, String clientIp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthLoginRequest(email, password))));
    }

    private java.sql.Timestamp lockedUntil(UUID userId) {
        List<java.sql.Timestamp> rows = jdbcTemplate.queryForList(
                "SELECT locked_until FROM account_lockout WHERE user_id = ?", java.sql.Timestamp.class, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int failedAttempts(UUID userId) {
        List<Integer> rows = jdbcTemplate.queryForList(
                "SELECT failed_attempts FROM account_lockout WHERE user_id = ?", Integer.class, userId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private List<String> auditActions(UUID userId) {
        return jdbcTemplate.queryForList(
                "SELECT action FROM audit_event WHERE entity_id = ?", String.class, userId);
    }

    private double counter(String name, String reason) {
        io.micrometer.core.instrument.search.Search search = meterRegistry.find(name);
        if (reason != null) {
            search = search.tag("reason", reason);
        }
        return search.counter().count();
    }

    /**
     * Quita del Problem Details lo que cambia entre dos peticiones cualesquiera
     * (correlationId y timestamp) para poder comparar el resto byte a byte.
     */
    private String withoutVolatileFields(String problemJson) throws Exception {
        Map<String, Object> problem = objectMapper.readValue(problemJson, Map.class);
        problem.remove("correlationId");
        problem.remove("timestamp");
        return objectMapper.writeValueAsString(new java.util.TreeMap<>(problem));
    }
}
