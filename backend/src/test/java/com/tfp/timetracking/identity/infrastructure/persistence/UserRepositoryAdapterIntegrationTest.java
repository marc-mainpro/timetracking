package com.tfp.timetracking.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de integracion (Testcontainers PostgreSQL) del adaptador
 * {@link UserRepositoryAdapter}: persiste y recupera un User a traves del
 * puerto {@link UserRepository}, verificando el mapeo completo (incluidos los
 * roles en {@code user_role}) y la unicidad global de email tras T204.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserRepositoryAdapterIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsAndRecoversUserWithRoles() {
        UUID tenantId = insertTenant();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        User user = User.reconstitute(
                userId,
                tenantId,
                "Jane.Doe@Example.com",
                "hashed-password",
                "Jane",
                "Doe",
                UserStatus.ACTIVE,
                Set.of(Role.EMPLOYEE, Role.TENANT_ADMIN),
                now,
                now);

        userRepository.save(user);

        User recovered = userRepository.findById(userId).orElseThrow();
        assertThat(recovered.id()).isEqualTo(userId);
        assertThat(recovered.tenantId()).isEqualTo(tenantId);
        assertThat(recovered.email()).isEqualTo(Email.of("jane.doe@example.com"));
        assertThat(recovered.passwordHash()).isEqualTo("hashed-password");
        assertThat(recovered.firstName()).isEqualTo("Jane");
        assertThat(recovered.lastName()).isEqualTo("Doe");
        assertThat(recovered.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(recovered.roles()).containsExactlyInAnyOrder(Role.EMPLOYEE, Role.TENANT_ADMIN);
        assertThat(recovered.createdAt()).isEqualTo(now);
        assertThat(recovered.updatedAt()).isEqualTo(now);
    }

    @Test
    void findByEmailLocatesUserGlobally() {
        UUID tenantId = insertTenant();
        User user = User.reconstitute(
                UUID.randomUUID(),
                tenantId,
                "lookup@example.com",
                "hash",
                "Look",
                "Up",
                UserStatus.ACTIVE,
                Set.of(Role.EMPLOYEE),
                Instant.now(),
                Instant.now());
        userRepository.save(user);

        assertThat(userRepository.findByEmail(Email.of("lookup@example.com"))).isPresent();
        assertThat(userRepository.existsByEmail(Email.of("lookup@example.com"))).isTrue();
        assertThat(userRepository.findByEmail(Email.of("missing@example.com"))).isEmpty();
    }

    @Test
    void rejectsSameEmailAcrossDifferentTenants() {
        UUID tenantA = insertTenant();
        UUID tenantB = insertTenant();

        userRepository.save(newUser(tenantA, "shared@example.com"));

        assertThatThrownBy(() -> userRepository.save(newUser(tenantB, "shared@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSameEmailWithinSameTenant() {
        UUID tenantId = insertTenant();
        userRepository.save(newUser(tenantId, "duplicate@example.com"));

        assertThatThrownBy(() -> userRepository.save(newUser(tenantId, "duplicate@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByIdReturnsEmptyWhenUserDoesNotExist() {
        assertThat(userRepository.findById(UUID.randomUUID())).isEmpty();
    }

    private User newUser(UUID tenantId, String email) {
        Instant now = Instant.now();
        return User.reconstitute(
                UUID.randomUUID(),
                tenantId,
                email,
                "hash",
                "First",
                "Last",
                UserStatus.ACTIVE,
                Set.of(Role.EMPLOYEE),
                now,
                now);
    }

    private UUID insertTenant() {
        UUID id = UUID.randomUUID();
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO tenant (id, name, status, timezone, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, "Tenant " + id, "ACTIVE", "Europe/Madrid", now, now);
        return id;
    }
}
