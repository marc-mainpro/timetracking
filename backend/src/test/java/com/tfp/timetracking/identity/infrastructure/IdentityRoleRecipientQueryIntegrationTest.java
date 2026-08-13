package com.tfp.timetracking.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.notification.application.NotificationRecipient;
import com.tfp.timetracking.notification.application.RoleRecipientQuery;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de integracion del puerto de destinatarios por rol (T170-01).
 *
 * <p>Cubre las tres reglas que sostienen el fan-out: solo usuarios activos,
 * nunca usuarios de otro tenant, y los administradores de plataforma resueltos
 * desde el tenant de sistema.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityRoleRecipientQueryIntegrationTest {

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
    private RoleRecipientQuery roleRecipientQuery;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsOnlyTheActiveAdminsOfTheRequestedTenant() {
        UUID tenantId = insertTenant();
        UUID otherTenantId = insertTenant();
        String uniqueSuffix = UUID.randomUUID().toString();
        User activeAdmin = save(tenantId, "activo-" + uniqueSuffix + "@acme.test", UserStatus.ACTIVE, Role.TENANT_ADMIN);
        save(tenantId, "inactivo-" + uniqueSuffix + "@acme.test", UserStatus.INACTIVE, Role.TENANT_ADMIN);
        save(tenantId, "empleado-" + uniqueSuffix + "@acme.test", UserStatus.ACTIVE, Role.EMPLOYEE);
        save(otherTenantId, "ajeno-" + uniqueSuffix + "@otra.test", UserStatus.ACTIVE, Role.TENANT_ADMIN);

        List<NotificationRecipient> recipients =
                roleRecipientQuery.findActiveByRole(tenantId, Role.TENANT_ADMIN.name());

        assertThat(recipients)
                .containsExactly(new NotificationRecipient(activeAdmin.id(), activeAdmin.email().value()));
    }

    @Test
    void returnsTheActivePlatformAdmins() {
        String email = "plataforma-" + UUID.randomUUID() + "@plataforma.test";
        User platformAdmin = save(PlatformTenant.ID, email, UserStatus.ACTIVE, Role.PLATFORM_ADMIN);
        save(PlatformTenant.ID, "baja-" + UUID.randomUUID() + "@plataforma.test", UserStatus.INACTIVE, Role.PLATFORM_ADMIN);

        List<NotificationRecipient> recipients = roleRecipientQuery.findActivePlatformAdmins();

        assertThat(recipients).contains(new NotificationRecipient(platformAdmin.id(), email));
        assertThat(recipients).allSatisfy(recipient -> assertThat(recipient.email()).isNotBlank());
    }

    @Test
    void returnsNoRecipientsForATenantWithoutAnyoneInThatRole() {
        UUID tenantId = insertTenant();

        assertThat(roleRecipientQuery.findActiveByRole(tenantId, Role.TENANT_ADMIN.name())).isEmpty();
    }

    @Test
    void treatsAnUnknownRoleAsNoRecipients() {
        // Un rol que no existe no es un fallo del sistema de notificaciones:
        // simplemente no hay a quien avisar.
        assertThat(roleRecipientQuery.findActiveByRole(insertTenant(), "ROL_INEXISTENTE")).isEmpty();
    }

    private User save(UUID tenantId, String email, UserStatus status, Role role) {
        Instant now = Instant.now();
        return userRepository.save(User.reconstitute(
                UUID.randomUUID(),
                tenantId,
                email,
                "hash",
                "Nombre",
                "Apellido",
                status,
                Set.of(role),
                now,
                now));
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
