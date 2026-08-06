package com.tfp.timetracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import javax.sql.DataSource;
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
class FlywayShiftMigrationIntegrationTest {

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
    private DataSource dataSource;

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
