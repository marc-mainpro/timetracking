package com.tfp.timetracking.corrections.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.corrections.domain.ProposedChanges;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorrectionRequestRepositoryAdapterIntegrationTest {

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
    private CorrectionRequestRepository correctionRequestRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsAndReloadsJsonbRoundTrip() {
        UUID tenantId = insertTenant();
        UUID employeeId = insertUser(tenantId, "correction@example.com");
        UUID workdayId = insertWorkday(tenantId, employeeId);
        CorrectionRequest correctionRequest = CorrectionRequest.request(
                tenantId,
                workdayId,
                employeeId,
                "Salida manual",
                validChanges(),
                Instant.parse("2026-01-16T09:00:00Z"),
                UUID::randomUUID);

        CorrectionRequest saved = correctionRequestRepository.save(correctionRequest);
        CorrectionRequest reloaded = correctionRequestRepository.findById(tenantId, saved.id()).orElseThrow();

        assertThat(reloaded.proposedChanges().startedAt()).isEqualTo(validChanges().startedAt());
        assertThat(reloaded.proposedChanges().breaks()).hasSize(1);
    }

    @Test
    void rejectsSecondPendingCorrectionForSameWorkdayAndRequester() {
        UUID tenantId = insertTenant();
        UUID employeeId = insertUser(tenantId, "correction2@example.com");
        UUID workdayId = insertWorkday(tenantId, employeeId);
        correctionRequestRepository.save(CorrectionRequest.request(
                tenantId,
                workdayId,
                employeeId,
                "Primera",
                validChanges(),
                Instant.now(),
                UUID::randomUUID));

        assertThatThrownBy(() -> correctionRequestRepository.save(CorrectionRequest.request(
                tenantId,
                workdayId,
                employeeId,
                "Segunda",
                validChanges(),
                Instant.now().plusSeconds(60),
                UUID::randomUUID))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private ProposedChanges validChanges() {
        return new ProposedChanges(
                Instant.parse("2026-01-15T09:00:00Z"),
                Instant.parse("2026-01-15T18:00:00Z"),
                List.of(new ProposedChanges.ProposedBreak(
                        Instant.parse("2026-01-15T12:00:00Z"), Instant.parse("2026-01-15T12:30:00Z"))));
    }

    private UUID insertTenant() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO tenant (id, name, status, timezone, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, "Tenant " + id, "ACTIVE", "Europe/Madrid", now, now);
        return id;
    }

    private UUID insertUser(UUID tenantId, String email) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO app_user (id, tenant_id, email, password_hash, first_name, last_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, email, "hash", "First", "Last", "ACTIVE", now, now);
        return id;
    }

    private UUID insertWorkday(UUID tenantId, UUID employeeId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO workday (id, tenant_id, employee_id, status, started_at, ended_at, version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, employeeId, "CLOSED", now, Timestamp.from(Instant.now().plusSeconds(60)), 0L, now, now);
        return id;
    }
}
