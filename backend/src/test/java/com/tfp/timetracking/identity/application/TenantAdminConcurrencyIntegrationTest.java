package com.tfp.timetracking.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.identity.domain.LastAdminException;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.identity.infrastructure.persistence.UserRepositoryAdapter;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.tenant.application.RegisterTenantCommand;
import com.tfp.timetracking.tenant.application.RegisterTenantResult;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantAdminConcurrencyIntegrationTest {

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
    private RegisterTenantUseCase registerTenantUseCase;

    @Autowired
    private CreateEmployeeUseCase createEmployeeUseCase;

    @Autowired
    private DeactivateEmployeeUseCase deactivateEmployeeUseCase;

    @Autowired
    private AssignRoleUseCase assignRoleUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ThreadLocalTenantContext tenantContext;

    @Autowired
    private LockingUserRepository lockingUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        lockingUserRepository.enableDelay();
    }

    @AfterEach
    void tearDown() {
        lockingUserRepository.disableDelay();
        tenantContext.clear();
    }

    @Test
    void preventsLeavingTenantWithoutAdminsWhenTwoAdminsAreDeactivatedInParallel() throws Exception {
        TenantAdmins tenantAdmins = createTenantWithTwoAdmins();

        List<Throwable> failures = executeConcurrently(
                () -> deactivateAsTenantAdmin(tenantAdmins.tenantId(), tenantAdmins.adminA().id()),
                () -> deactivateAsTenantAdmin(tenantAdmins.tenantId(), tenantAdmins.adminB().id()));

        assertThat(failures).hasSize(1).first().isInstanceOf(LastAdminException.class);
        assertThat(activeAdminIds(tenantAdmins.tenantId()))
                .hasSize(1)
                .containsAnyOf(tenantAdmins.adminA().id(), tenantAdmins.adminB().id());
    }

    @Test
    void preventsLeavingTenantWithoutAdminsWhenTwoAdminsLoseRoleInParallel() throws Exception {
        TenantAdmins tenantAdmins = createTenantWithTwoAdmins();

        List<Throwable> failures = executeConcurrently(
                () -> removeAdminRoleAsTenantAdmin(tenantAdmins.tenantId(), tenantAdmins.adminA().id()),
                () -> removeAdminRoleAsTenantAdmin(tenantAdmins.tenantId(), tenantAdmins.adminB().id()));

        assertThat(failures).hasSize(1).first().isInstanceOf(LastAdminException.class);
        assertThat(activeAdminIds(tenantAdmins.tenantId())).hasSize(1);
    }

    private TenantAdmins createTenantWithTwoAdmins() {
        String suffix = UUID.randomUUID().toString();
        RegisterTenantResult registered = registerTenantUseCase.register(new RegisterTenantCommand(
                "Tenant Admin Lock " + suffix,
                "Europe/Madrid",
                "admin-" + suffix + "@acme.test",
                "supersecretpwd",
                "Jane",
                "Doe"));
        tenantContext.set(registered.tenantId(), registered.adminUserId(), Set.of("TENANT_ADMIN"));
        User secondAdmin = createEmployeeUseCase.create(new CreateEmployeeCommand(
                "coadmin-" + suffix + "@acme.test",
                "supersecretpwd",
                "John",
                "Doe",
                Set.of(Role.TENANT_ADMIN.name())));
        User firstAdmin = userRepository.findById(registered.tenantId(), registered.adminUserId()).orElseThrow();
        tenantContext.clear();
        return new TenantAdmins(registered.tenantId(), firstAdmin, secondAdmin);
    }

    private void deactivateAsTenantAdmin(UUID tenantId, UUID employeeId) {
        tenantContext.set(tenantId, employeeId, Set.of("TENANT_ADMIN"));
        try {
            deactivateEmployeeUseCase.deactivate(employeeId);
        } finally {
            tenantContext.clear();
        }
    }

    private void removeAdminRoleAsTenantAdmin(UUID tenantId, UUID employeeId) {
        tenantContext.set(tenantId, employeeId, Set.of("TENANT_ADMIN"));
        try {
            assignRoleUseCase.assign(new EmployeeRolesCommand(employeeId, Set.of(Role.EMPLOYEE.name())));
        } finally {
            tenantContext.clear();
        }
    }

    private List<UUID> activeAdminIds(UUID tenantId) {
        return jdbcTemplate.queryForList(
                """
                select distinct u.id
                from app_user u
                join user_role ur on ur.user_id = u.id
                where u.tenant_id = ?
                  and u.status = 'ACTIVE'
                  and ur.role = 'TENANT_ADMIN'
                """,
                UUID.class,
                tenantId);
    }

    private List<Throwable> executeConcurrently(ThrowingRunnable first, ThrowingRunnable second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> firstFuture = executor.submit(task(first, start, failures));
            Future<?> secondFuture = executor.submit(task(second, start, failures));
            start.countDown();
            firstFuture.get(10, TimeUnit.SECONDS);
            secondFuture.get(10, TimeUnit.SECONDS);
        }
        return failures;
    }

    private Callable<Void> task(ThrowingRunnable runnable, CountDownLatch start, List<Throwable> failures) {
        return () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                runnable.run();
            } catch (Throwable throwable) {
                failures.add(throwable);
            }
            return null;
        };
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    record TenantAdmins(UUID tenantId, User adminA, User adminB) {
    }

    static final class ThreadLocalTenantContext implements TenantContext {

        private final ThreadLocal<UUID> tenantId = new ThreadLocal<>();
        private final ThreadLocal<UUID> userId = new ThreadLocal<>();
        private final ThreadLocal<Set<String>> roles = new ThreadLocal<>();

        void set(UUID tenantId, UUID userId, Set<String> roles) {
            this.tenantId.set(tenantId);
            this.userId.set(userId);
            this.roles.set(Set.copyOf(roles));
        }

        void clear() {
            tenantId.remove();
            userId.remove();
            roles.remove();
        }

        @Override
        public UUID currentTenantId() {
            return tenantId.get();
        }

        @Override
        public UUID currentUserId() {
            return userId.get();
        }

        @Override
        public Set<String> currentRoles() {
            return roles.get();
        }
    }

    static final class LockingUserRepository implements UserRepository {

        private final UserRepositoryAdapter delegate;
        private final AtomicInteger lockCalls = new AtomicInteger();
        private volatile boolean delayEnabled;

        LockingUserRepository(UserRepositoryAdapter delegate) {
            this.delegate = delegate;
        }

        void enableDelay() {
            lockCalls.set(0);
            delayEnabled = true;
        }

        void disableDelay() {
            delayEnabled = false;
            lockCalls.set(0);
        }

        @Override
        public void lockActiveAdmins(UUID tenantId) {
            delegate.lockActiveAdmins(tenantId);
            if (delayEnabled && lockCalls.incrementAndGet() == 1) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupcion inesperada durante la prueba de concurrencia", interruptedException);
                }
            }
        }

        @Override
        public User save(User user) {
            return delegate.save(user);
        }

        @Override
        public java.util.Optional<User> findById(UUID tenantId, UUID id) {
            return delegate.findById(tenantId, id);
        }

        @Override
        public java.util.Optional<User> findById(UUID id) {
            return delegate.findById(id);
        }

        @Override
        public java.util.Optional<User> findByEmail(com.tfp.timetracking.identity.domain.Email email) {
            return delegate.findByEmail(email);
        }

        @Override
        public boolean existsByEmail(com.tfp.timetracking.identity.domain.Email email) {
            return delegate.existsByEmail(email);
        }

        @Override
        public java.util.List<User> findAllByTenantId(UUID tenantId) {
            return delegate.findAllByTenantId(tenantId);
        }

        @Override
        public com.tfp.timetracking.shared.domain.PagedResult<User> findByTenant(
                UUID tenantId,
                UserStatus status,
                int page,
                int size) {
            return delegate.findByTenant(tenantId, status, page, size);
        }

        @Override
        public long countActiveAdmins(UUID tenantId) {
            return delegate.countActiveAdmins(tenantId);
        }

        @Override
        public long countActiveAdminsExcludingUser(UUID tenantId, UUID userId) {
            return delegate.countActiveAdminsExcludingUser(tenantId, userId);
        }
    }

    @TestConfiguration
    static class ConcurrencyTestConfiguration {

        @Bean
        @Primary
        ThreadLocalTenantContext tenantContext() {
            return new ThreadLocalTenantContext();
        }

        @Bean
        @Primary
        LockingUserRepository userRepository(UserRepositoryAdapter delegate) {
            return new LockingUserRepository(delegate);
        }

        @Bean
        @Primary
        DomainEventPublisher domainEventPublisher() {
            return events -> {
            };
        }

    }
}
