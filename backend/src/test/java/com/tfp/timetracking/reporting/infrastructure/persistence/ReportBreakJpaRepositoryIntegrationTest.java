package com.tfp.timetracking.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
class ReportBreakJpaRepositoryIntegrationTest {

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
    private ReportBreakJpaRepository reportBreakJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findsClosedBreaksOnlyWithinTheRequestedTenant() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID employeeA = insertEmployee(tenantA, "employee-a@acme.test");
        UUID employeeB = insertEmployee(tenantB, "employee-b@acme.test");
        UUID workdayA = insertWorkday(tenantA, employeeA);
        UUID workdayB = insertWorkday(tenantB, employeeB);
        insertClosedBreak(workdayA, Instant.parse("2026-02-10T12:00:00Z"), Instant.parse("2026-02-10T12:30:00Z"));
        insertClosedBreak(workdayB, Instant.parse("2026-02-10T13:00:00Z"), Instant.parse("2026-02-10T13:30:00Z"));

        List<ReportBreakRow> rows = reportBreakJpaRepository.findClosedBreaksByWorkdayIds(tenantA, List.of(workdayA, workdayB));

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().workdayId()).isEqualTo(workdayA);
    }

    private UUID insertEmployee(UUID tenantId, String email) {
        insertTenant(tenantId);
        UUID userId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO app_user (id, tenant_id, email, password_hash, first_name, last_name, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId,
                tenantId,
                email,
                "noop",
                "Test",
                "User",
                "ACTIVE",
                now,
                now);
        return userId;
    }

    private void insertTenant(UUID tenantId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO tenant (id, name, status, timezone, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                tenantId,
                "Tenant " + tenantId,
                "ACTIVE",
                "Europe/Madrid",
                now,
                now);
    }

    private UUID insertWorkday(UUID tenantId, UUID employeeId) {
        UUID workdayId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO workday (id, tenant_id, employee_id, status, started_at, ended_at, version, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                workdayId,
                tenantId,
                employeeId,
                "CLOSED",
                Timestamp.from(Instant.parse("2026-02-10T08:00:00Z")),
                Timestamp.from(Instant.parse("2026-02-10T17:00:00Z")),
                0L,
                now,
                now);
        return workdayId;
    }

    private void insertClosedBreak(UUID workdayId, Instant startedAt, Instant endedAt) {
        jdbcTemplate.update(
                "INSERT INTO break_entry (id, workday_id, started_at, ended_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                workdayId,
                Timestamp.from(startedAt),
                Timestamp.from(endedAt));
    }
}
