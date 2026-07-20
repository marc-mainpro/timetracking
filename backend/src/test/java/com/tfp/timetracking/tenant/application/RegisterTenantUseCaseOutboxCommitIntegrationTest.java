package com.tfp.timetracking.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Prueba de integracion (Testcontainers PostgreSQL) de la ficha T702: una
 * accion de negocio real que hace commit (registrar un tenant) debe dejar
 * los eventos de integracion correspondientes en {@code outbox_message} en
 * estado {@code PENDING}, escritos en la misma transaccion que el alta del
 * tenant y del usuario admin.
 *
 * <p>{@link RegisterTenantUseCase} publica dos eventos de dominio en la
 * misma llamada a {@code publish(...)} ({@code TenantRegistered} y {@code
 * EmployeeCreated}), asi que este test tambien verifica que el {@code
 * DomainEventPublisher} traduce correctamente eventos de mas de un modulo
 * en la misma invocacion.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegisterTenantUseCaseOutboxCommitIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Test
    void committingRegistrationWritesPendingOutboxMessagesForTenantAndAdminEvents() {
        String tenantName = "Outbox Commit Corp " + UUID.randomUUID();
        RegisterTenantCommand command = new RegisterTenantCommand(
                tenantName,
                "Europe/Madrid",
                "outbox-commit-admin+" + UUID.randomUUID() + "@acme.test",
                "supersecretpwd",
                "Jane",
                "Doe");

        RegisterTenantResult result = registerTenantUseCase.register(command);

        String tenantEventStatus = jdbcTemplate.queryForObject(
                "select status from outbox_message where aggregate_id = ?::uuid and event_type = 'tenant.registered.v1'",
                String.class,
                result.tenantId().toString());
        assertThat(tenantEventStatus).isEqualTo("PENDING");

        String adminEventStatus = jdbcTemplate.queryForObject(
                "select status from outbox_message where aggregate_id = ?::uuid and event_type = 'identity.employee-created.v1'",
                String.class,
                result.adminUserId().toString());
        assertThat(adminEventStatus).isEqualTo("PENDING");

        Long tenantIdColumn = jdbcTemplate.queryForObject(
                "select count(*) from outbox_message where tenant_id = ?::uuid",
                Long.class,
                result.tenantId().toString());
        assertThat(tenantIdColumn).isEqualTo(2L);
    }
}
