package com.tfp.timetracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.support.AbstractFlywayMigrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywayShiftMigrationIntegrationTest extends AbstractFlywayMigrationTest {

    @Test
    void appliesShiftMigrationFromEmptyDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "shift_template")).isTrue();
            assertThat(tableExists(connection, "shift_assignment")).isTrue();
        }
    }

    @Test
    void rejectsDuplicateTemplateNameInSameTenant() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Shift A");

        insertTemplate(jdbc, UUID.randomUUID(), tenantId, "General");

        assertThatThrownBy(() -> insertTemplate(jdbc, UUID.randomUUID(), tenantId, "General"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidAssignmentPeriod() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Shift B");
        UUID employeeId = insertUser(jdbc, tenantId, "shift@example.com");
        UUID templateId = insertTemplate(jdbc, UUID.randomUUID(), tenantId, "General");

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO shift_assignment (id, tenant_id, employee_id, shift_template_id, valid_from, valid_to) VALUES (?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        tenantId,
                        employeeId,
                        templateId,
                        LocalDate.of(2026, 9, 10),
                        LocalDate.of(2026, 9, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static UUID insertTenant(JdbcTemplate jdbc, String name) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(java.time.Instant.now());
        jdbc.update(
                "INSERT INTO tenant (id, name, status, timezone, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, name, "ACTIVE", "Europe/Madrid", now, now);
        return id;
    }

    private static UUID insertUser(JdbcTemplate jdbc, UUID tenantId, String email) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(java.time.Instant.now());
        jdbc.update(
                "INSERT INTO app_user (id, tenant_id, email, password_hash, first_name, last_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, email, "hash", "First", "Last", "ACTIVE", now, now);
        return id;
    }

    private static UUID insertTemplate(JdbcTemplate jdbc, UUID id, UUID tenantId, String name) {
        jdbc.update(
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

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"})) {
            return resultSet.next();
        }
    }
}
