package com.tfp.timetracking.shift.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.support.AbstractPostgresDataJpaTest;
import com.tfp.timetracking.shift.domain.model.ShiftAssignment;
import com.tfp.timetracking.shift.domain.model.ShiftAssignmentRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import(ShiftAssignmentRepositoryAdapter.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ShiftAssignmentRepositoryAdapterIntegrationTest extends AbstractPostgresDataJpaTest {

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
