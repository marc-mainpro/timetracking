package com.tfp.timetracking.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.shared.domain.PagedResult;
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

        User recovered = userRepository.findById(tenantId, userId).orElseThrow();
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
    void tenantAwareFindByIdReturnsEmptyForWrongTenant() {
        UUID tenantA = insertTenant();
        UUID tenantB = insertTenant();
        User user = newUser(tenantA, "tenant-aware@example.com");
        userRepository.save(user);

        assertThat(userRepository.findById(tenantA, user.id())).isPresent();
        assertThat(userRepository.findById(tenantB, user.id())).isEmpty();
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
        assertThat(userRepository.findById(UUID.randomUUID(), UUID.randomUUID())).isEmpty();
        assertThat(userRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void listByTenantSupportsPagingAndStatusFilter() {
        UUID tenantId = insertTenant();
        userRepository.save(newUser(tenantId, "a@example.com"));
        User inactive = newUser(tenantId, "b@example.com");
        inactive.deactivate(() -> Instant.now(), UUID::randomUUID);
        userRepository.save(inactive);

        PagedResult<User> all = userRepository.findByTenant(tenantId, null, null, null, 0, 10);
        PagedResult<User> inactiveOnly = userRepository.findByTenant(tenantId, UserStatus.INACTIVE, null, null, 0, 10);

        assertThat(all.content()).hasSize(2);
        assertThat(inactiveOnly.content()).hasSize(1);
        assertThat(inactiveOnly.content().get(0).status()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void listByTenantFiltersByRoleWithoutBreakingPaging() {
        UUID tenantId = insertTenant();
        userRepository.save(newUser(tenantId, "empleado@example.com"));
        userRepository.save(withRoles(tenantId, "mixto@example.com", Set.of(Role.TENANT_ADMIN, Role.EMPLOYEE)));
        userRepository.save(withRoles(tenantId, "solo-admin@example.com", Set.of(Role.TENANT_ADMIN)));

        PagedResult<User> employees = userRepository.findByTenant(tenantId, null, Role.EMPLOYEE, null, 0, 10);

        assertThat(employees.content()).extracting(user -> user.email().toString())
                .containsExactly("empleado@example.com", "mixto@example.com");
        // El total cuenta usuarios y no filas de rol: quien acumula dos roles no
        // puede contarse dos veces o la paginacion mentiria.
        assertThat(employees.totalElements()).isEqualTo(2);
        assertThat(employees.totalPages()).isEqualTo(1);

        PagedResult<User> firstPage = userRepository.findByTenant(tenantId, null, Role.EMPLOYEE, null, 0, 1);
        assertThat(firstPage.content()).hasSize(1);
        assertThat(firstPage.totalElements()).isEqualTo(2);
        assertThat(firstPage.totalPages()).isEqualTo(2);
    }

    @Test
    void listByTenantCombinesStatusAndRoleFilters() {
        UUID tenantId = insertTenant();
        userRepository.save(newUser(tenantId, "activo@example.com"));
        User inactive = newUser(tenantId, "inactivo@example.com");
        inactive.deactivate(() -> Instant.now(), UUID::randomUUID);
        userRepository.save(inactive);
        userRepository.save(withRoles(tenantId, "admin@example.com", Set.of(Role.TENANT_ADMIN)));

        PagedResult<User> activeEmployees =
                userRepository.findByTenant(tenantId, UserStatus.ACTIVE, Role.EMPLOYEE, null, 0, 10);

        assertThat(activeEmployees.content()).extracting(user -> user.email().toString())
                .containsExactly("activo@example.com");
    }

    @Test
    void listByTenantWithRoleNeverCrossesTenants() {
        UUID tenantId = insertTenant();
        UUID otherTenantId = insertTenant();
        userRepository.save(newUser(tenantId, "propio@example.com"));
        userRepository.save(newUser(otherTenantId, "ajeno@example.com"));

        PagedResult<User> employees = userRepository.findByTenant(tenantId, null, Role.EMPLOYEE, null, 0, 10);

        assertThat(employees.content()).extracting(user -> user.email().toString())
                .containsExactly("propio@example.com");
    }

    @Test
    void countsActiveAdminsByTenant() {
        UUID tenantId = insertTenant();
        User admin = User.reconstitute(
                UUID.randomUUID(),
                tenantId,
                "admin-count@example.com",
                "hash",
                "Admin",
                "One",
                UserStatus.ACTIVE,
                Set.of(Role.TENANT_ADMIN),
                Instant.now(),
                Instant.now());
        userRepository.save(admin);
        userRepository.save(newUser(tenantId, "employee-count@example.com"));

        assertThat(userRepository.countActiveAdmins(tenantId)).isEqualTo(1);
        assertThat(userRepository.countActiveAdminsExcludingUser(tenantId, admin.id())).isZero();
    }

    @Test
    void listByTenantFiltersByQueryAgainstEmailAndFullName() {
        UUID tenantId = insertTenant();
        userRepository.save(withIdentity(tenantId, "ana.ruiz@example.com", "Ana", "Ruiz", Set.of(Role.EMPLOYEE)));
        userRepository.save(withIdentity(tenantId, "luis.soto@example.com", "Luis", "Soto", Set.of(Role.EMPLOYEE)));

        PagedResult<User> byEmail = userRepository.findByTenant(tenantId, null, null, "luis.soto", 0, 10);
        PagedResult<User> byName = userRepository.findByTenant(tenantId, null, null, "ana ruiz", 0, 10);
        PagedResult<User> blank = userRepository.findByTenant(tenantId, null, null, "", 0, 10);

        assertThat(byEmail.content()).extracting(user -> user.email().toString()).containsExactly("luis.soto@example.com");
        assertThat(byName.content()).extracting(user -> user.email().toString()).containsExactly("ana.ruiz@example.com");
        assertThat(blank.content()).hasSize(2);
    }

    @Test
    void listByTenantCombinesQueryRoleAndTenantFilters() {
        UUID tenantId = insertTenant();
        UUID otherTenantId = insertTenant();
        userRepository.save(withIdentity(tenantId, "luis.employee@example.com", "Luis", "Empleado", Set.of(Role.EMPLOYEE)));
        userRepository.save(withIdentity(tenantId, "luis.admin@example.com", "Luis", "Admin", Set.of(Role.TENANT_ADMIN)));
        userRepository.save(withIdentity(otherTenantId, "luis.other@example.com", "Luis", "Otro", Set.of(Role.EMPLOYEE)));

        PagedResult<User> employees = userRepository.findByTenant(tenantId, null, Role.EMPLOYEE, "luis", 0, 10);

        assertThat(employees.content()).extracting(user -> user.email().toString())
                .containsExactly("luis.employee@example.com");
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

    private User withRoles(UUID tenantId, String email, Set<Role> roles) {
        return withIdentity(tenantId, email, "First", "Last", roles);
    }

    private User withIdentity(UUID tenantId, String email, String firstName, String lastName, Set<Role> roles) {
        Instant now = Instant.now();
        return User.reconstitute(UUID.randomUUID(), tenantId, email, "hash", firstName, lastName, UserStatus.ACTIVE, roles, now, now);
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
