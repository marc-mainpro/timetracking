package com.tfp.timetracking.shift.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.shift.domain.model.ShiftAssignment;
import com.tfp.timetracking.shift.domain.model.ShiftAssignmentRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
class ShiftAssignmentRepositoryAdapterIntegrationTest {

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
    private ShiftAssignmentRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesAndFindsEffectiveAssignmentByEmployeeAndDate() {
        UUID tenantId = insertTenant();
        UUID employeeId = insertUser(tenantId, "shift-employee@example.com");
        UUID templateId = insertTemplate(tenantId, "General");
        ShiftAssignment assignment = ShiftAssignment.create(
                tenantId,
                employeeId,
                templateId,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                UUID.randomUUID());

        ShiftAssignment saved = repository.save(assignment);

        assertThat(repository.findById(tenantId, saved.id())).isPresent();
        assertThat(repository.findEffectiveByEmployee(tenantId, employeeId, LocalDate.of(2026, 9, 15))).hasSize(1);
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

    private UUID insertTemplate(UUID tenantId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO shift_template (id, tenant_id, name, start_time, end_time, planned_break_minutes, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                tenantId,
                name,
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                30,
                "ACTIVE");
        return id;
    }
}
