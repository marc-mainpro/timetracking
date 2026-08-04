package com.tfp.timetracking.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del aprovisionamiento idempotente del PLATFORM_ADMIN inicial (T50-04)
 * usando un repositorio en memoria.
 */
class PlatformAdminBootstrapTest {

    private final Clock clock = () -> Instant.parse("2026-07-24T10:00:00Z");
    private final IdGenerator idGenerator = UUID::randomUUID;
    private final PasswordHasher passwordHasher = new PasswordHasher() {
        @Override
        public String hash(String raw) {
            return "hashed:" + raw;
        }

        @Override
        public boolean matches(String raw, String hash) {
            return hash.equals("hashed:" + raw);
        }
    };

    @Test
    void createsPlatformAdminInSystemTenantWhenConfiguredAndAbsent() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        bootstrap(repository, "admin@plataforma.com", "s3creta").run(null);

        assertThat(repository.saved).hasSize(1);
        User admin = repository.saved.get(0);
        assertThat(admin.tenantId()).isEqualTo(PlatformTenant.ID);
        assertThat(admin.roles()).containsExactly(Role.PLATFORM_ADMIN);
        assertThat(admin.email().value()).isEqualTo("admin@plataforma.com");
        assertThat(admin.passwordHash()).isEqualTo("hashed:s3creta");
    }

    @Test
    void isIdempotentWhenAdminAlreadyExists() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        repository.existing = true;

        bootstrap(repository, "admin@plataforma.com", "s3creta").run(null);

        assertThat(repository.saved).isEmpty();
    }

    @Test
    void doesNothingWhenNotConfigured() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        bootstrap(repository, "", "").run(null);
        bootstrap(repository, "admin@plataforma.com", "  ").run(null);

        assertThat(repository.saved).isEmpty();
    }

    private PlatformAdminBootstrap bootstrap(InMemoryUserRepository repository, String email, String password) {
        return new PlatformAdminBootstrap(repository, passwordHasher, clock, idGenerator, email, password);
    }

    private static final class InMemoryUserRepository
            implements com.tfp.timetracking.identity.domain.UserRepository {
        private final List<User> saved = new ArrayList<>();
        private boolean existing;

        @Override
        public User save(User user) {
            saved.add(user);
            return user;
        }

        @Override
        public boolean existsByEmail(Email email) {
            return existing;
        }

        @Override
        public void lockActiveAdmins(UUID tenantId) {
            // Doble en memoria y sin concurrencia: no hay filas que bloquear.
        }

        @Override
        public java.util.Optional<User> findById(UUID tenantId, UUID id) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<User> findById(UUID id) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<User> findByEmail(Email email) {
            return java.util.Optional.empty();
        }

        @Override
        public List<User> findAllByTenantId(UUID tenantId) {
            return List.of();
        }

        @Override
        public com.tfp.timetracking.shared.domain.PagedResult<User> findByTenant(
                UUID tenantId, com.tfp.timetracking.identity.domain.UserStatus status, int page, int size) {
            return null;
        }

        @Override
        public long countActiveAdmins(UUID tenantId) {
            return 0;
        }

        @Override
        public long countActiveAdminsExcludingUser(UUID tenantId, UUID userId) {
            return 0;
        }
    }
}
