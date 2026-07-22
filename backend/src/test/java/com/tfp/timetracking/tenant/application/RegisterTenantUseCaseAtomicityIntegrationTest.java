package com.tfp.timetracking.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

/**
 * Prueba de integracion (Testcontainers PostgreSQL) de la atomicidad de
 * {@link RegisterTenantUseCase} (ficha T203): si falla la persistencia del
 * usuario admin, la transaccion completa se revierte y no queda un tenant
 * huerfano en base de datos.
 *
 * <p>Sustituye el {@link UserRepository} real por un doble que falla al
 * guardar, manteniendo el {@code TenantRepository} real (Testcontainers) para
 * comprobar el estado efectivo de la tabla {@code tenant} tras el rollback.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegisterTenantUseCaseAtomicityIntegrationTest {

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
    private RegisterTenantUseCase registerTenantUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void doesNotLeaveOrphanTenantWhenAdminPersistenceFails() {
        String tenantName = "Atomic Corp " + UUID.randomUUID();
        RegisterTenantCommand command = new RegisterTenantCommand(
                tenantName, "Europe/Madrid", "atomic-admin@acme.test", "supersecretpwd", "Jane", "Doe");

        assertThatThrownBy(() -> registerTenantUseCase.register(command)).isInstanceOf(IllegalStateException.class);

        Long tenantCount =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tenant WHERE name = ?", Long.class, tenantName);
        assertThat(tenantCount).isEqualTo(0L);
    }

    @TestConfiguration
    static class FailingUserRepositoryConfiguration {

        @Bean
        @Primary
        UserRepository failingUserRepository() {
            return new UserRepository() {
                @Override
                public User save(User user) {
                    throw new IllegalStateException("Fallo simulado al persistir el usuario admin");
                }

                @Override
                public Optional<User> findById(UUID id) {
                    return Optional.empty();
                }

                @Override
                public Optional<User> findById(UUID tenantId, UUID id) {
                    return Optional.empty();
                }

                @Override
                public Optional<User> findByEmail(Email email) {
                    return Optional.empty();
                }

                @Override
                public boolean existsByEmail(Email email) {
                    return false;
                }

                @Override
                public List<User> findAllByTenantId(UUID tenantId) {
                    return List.of();
                }

                @Override
                public PagedResult<User> findByTenant(UUID tenantId, UserStatus status, int page, int size) {
                    return new PagedResult<>(List.of(), page, size, 0, 0);
                }

                @Override
                public void lockActiveAdmins(UUID tenantId) {
                }

                @Override
                public long countActiveAdmins(UUID tenantId) {
                    return 0;
                }

                @Override
                public long countActiveAdminsExcludingUser(UUID tenantId, UUID userId) {
                    return 0;
                }
            };
        }
    }
}
