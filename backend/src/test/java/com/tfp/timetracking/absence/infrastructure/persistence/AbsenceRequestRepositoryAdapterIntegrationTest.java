package com.tfp.timetracking.absence.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.absence.domain.AbsenceRequest;
import com.tfp.timetracking.absence.domain.AbsenceRequestRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AbsenceRequestRepositoryAdapterIntegrationTest {

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
    private AbsenceRequestRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesAndFindsOverlappingRequestsByEmployee() {
        UUID tenantId = insertTenant();
        UUID employeeId = insertUser(tenantId, "absence-request@example.com");
        UUID absenceTypeId = insertAbsenceType(tenantId, "VAC");
        AbsenceRequest request = AbsenceRequest.request(
                tenantId,
                employeeId,
                absenceTypeId,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                "Vacaciones",
                Instant.parse("2026-08-01T10:00:00Z"),
                UUID::randomUUID);

        AbsenceRequest saved = repository.save(request);

        assertThat(repository.findById(tenantId, saved.id())).isPresent();
        assertThat(repository.findByEmployeeAndDateRange(
                        tenantId, employeeId, LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 20)))
                .hasSize(1);
    }

    @Test
    void findsOnlyApprovedRequestsInDateRange() {
        UUID tenantId = insertTenant();
        UUID employeeId = insertUser(tenantId, "absence-approved@example.com");
        UUID absenceTypeId = insertAbsenceType(tenantId, "VAC");
        AbsenceRequest approved = AbsenceRequest.request(
                tenantId,
                employeeId,
                absenceTypeId,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                "Vacaciones",
                Instant.parse("2026-08-01T10:00:00Z"),
                UUID::randomUUID);
        approved.pullDomainEvents();
        approved.approve(UUID.randomUUID(), null, Instant.parse("2026-08-02T10:00:00Z"), UUID::randomUUID);
        repository.save(approved);

        assertThat(repository.findApprovedByEmployeeAndDateRange(
                        tenantId, employeeId, LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 11)))
                .hasSize(1);
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

    private UUID insertAbsenceType(UUID tenantId, String code) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO absence_type (id, tenant_id, code, name, requires_approval, allows_attachment, active) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                tenantId,
                code,
                code,
                true,
                false,
                true);
        return id;
    }
}
