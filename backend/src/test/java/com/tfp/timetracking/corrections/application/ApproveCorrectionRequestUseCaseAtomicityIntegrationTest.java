package com.tfp.timetracking.corrections.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.corrections.domain.ProposedChanges;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.application.RegisterTenantCommand;
import com.tfp.timetracking.tenant.application.RegisterTenantResult;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Prueba de integracion (Testcontainers PostgreSQL) de la atomicidad del
 * Transactional Outbox (ficha T702): si el caso de uso falla DESPUES de que
 * {@code DomainEventPublisher.publish(...)} ya escribio en el outbox pero
 * ANTES de que la transaccion termine, el rollback de Spring debe deshacer
 * TAMBIEN esa escritura. No debe quedar ninguna fila en {@code
 * outbox_message}: esta es la prueba de atomicidad mas importante de T702.
 *
 * <p>Se usa {@link ApproveCorrectionRequestUseCase} porque, a diferencia de
 * otros casos de uso emisores, invoca {@code auditRecorder.record(...)}
 * DESPUES de {@code domainEventPublisher.publish(...)}: forzando un fallo ahi
 * se garantiza que el mensaje de outbox ya se habia escrito (en memoria de la
 * transaccion) cuando se produce el fallo.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApproveCorrectionRequestUseCaseAtomicityIntegrationTest {

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
    private WorkdayRepository workdayRepository;

    @Autowired
    private CorrectionRequestRepository correctionRequestRepository;

    @Autowired
    private ApproveCorrectionRequestUseCase approveCorrectionRequestUseCase;

    @Autowired
    private MutableTenantContext mutableTenantContext;

    @Autowired
    private Clock clock;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rollsBackOutboxWriteWhenUseCaseFailsAfterPublishButBeforeCommit() {
        String tenantName = "Atomic Rollback Corp " + UUID.randomUUID();
        RegisterTenantResult tenant = registerTenantUseCase.register(new RegisterTenantCommand(
                tenantName, "Europe/Madrid", "atomic-rollback-admin@acme.test", "supersecretpwd", "Jane", "Doe"));

        Instant startedAt = Instant.parse("2026-07-20T09:00:00Z");
        Instant endedAt = Instant.parse("2026-07-20T17:00:00Z");
        Workday workday = Workday.start(tenant.tenantId(), tenant.adminUserId(), startedAt, idGenerator);
        workday.close(endedAt, idGenerator);
        workday.pullDomainEvents();
        workdayRepository.save(workday);

        ProposedChanges proposedChanges =
                new ProposedChanges(startedAt.plusSeconds(300), endedAt.plusSeconds(300), List.of());
        CorrectionRequest correctionRequest = CorrectionRequest.request(
                tenant.tenantId(), workday.id(), tenant.adminUserId(), "Ajuste de prueba", proposedChanges, clock.now(), idGenerator);
        correctionRequest.pullDomainEvents();
        correctionRequestRepository.save(correctionRequest);

        mutableTenantContext.tenantId = tenant.tenantId();
        mutableTenantContext.userId = tenant.adminUserId();

        assertThatThrownBy(() -> approveCorrectionRequestUseCase.approve(
                        new ResolveCorrectionCommand(correctionRequest.id(), "Aprobada en prueba de atomicidad")))
                .isInstanceOf(RuntimeException.class);

        Long outboxRowsForCorrection = jdbcTemplate.queryForObject(
                "select count(*) from outbox_message where aggregate_id = ?::uuid", Long.class, correctionRequest.id().toString());
        assertThat(outboxRowsForCorrection).isZero();

        String correctionStatus = jdbcTemplate.queryForObject(
                "select status from correction_request where id = ?::uuid", String.class, correctionRequest.id().toString());
        assertThat(correctionStatus).isEqualTo("PENDING");

        String workdayStatus = jdbcTemplate.queryForObject(
                "select status from workday where id = ?::uuid", String.class, workday.id().toString());
        assertThat(workdayStatus).isEqualTo("CLOSED");

        Long auditRows = jdbcTemplate.queryForObject(
                "select count(*) from audit_event where entity_id = ?::uuid", Long.class, correctionRequest.id().toString());
        assertThat(auditRows).isZero();
    }

    static final class MutableTenantContext implements TenantContext {

        private volatile UUID tenantId;
        private volatile UUID userId;

        @Override
        public UUID currentTenantId() {
            return tenantId;
        }

        @Override
        public UUID currentUserId() {
            return userId;
        }

        @Override
        public Set<String> currentRoles() {
            return Set.of("TENANT_ADMIN");
        }
    }

    @TestConfiguration
    static class AtomicityRollbackTestConfiguration {

        @Bean
        @Primary
        MutableTenantContext mutableTenantContext() {
            return new MutableTenantContext();
        }

        @Bean
        @Primary
        AuditRecorder failingAuditRecorder() {
            return (action, entityType, entityId, metadata) -> {
                throw new IllegalStateException("Fallo simulado en auditoria tras escribir en el outbox");
            };
        }
    }
}
