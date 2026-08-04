package com.tfp.timetracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDate;
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

/**
 * Migracion V16 aplicada desde base limpia (T70-03). Comprueba que las tablas
 * existen y que las restricciones que sostienen las invariantes del dominio
 * estan realmente en la base de datos, no solo en el codigo.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlywayCalendarMigrationIntegrationTest {

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
    void appliesCalendarMigrationFromEmptyDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "work_calendar")).isTrue();
            assertThat(tableExists(connection, "calendar_day_rule")).isTrue();
            assertThat(tableExists(connection, "calendar_holiday")).isTrue();
            assertThat(tableExists(connection, "calendar_special_day")).isTrue();
            assertThat(tableExists(connection, "calendar_assignment")).isTrue();
        }
    }

    @Test
    void rejectsTwoCalendarsWithTheSameNameInTheSameTenant() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Calendar A");

        insertCalendar(jdbc, UUID.randomUUID(), tenantId, "General");

        assertThatThrownBy(() -> insertCalendar(jdbc, UUID.randomUUID(), tenantId, "General"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsTheSameCalendarNameInDifferentTenants() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantA = insertTenant(jdbc, "Calendar B");
        UUID tenantB = insertTenant(jdbc, "Calendar C");

        insertCalendar(jdbc, UUID.randomUUID(), tenantA, "Compartido");
        insertCalendar(jdbc, UUID.randomUUID(), tenantB, "Compartido");

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM work_calendar WHERE name = 'Compartido'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void rejectsValidityEndingBeforeItStarts() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Calendar D");

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO work_calendar
                            (id, tenant_id, name, timezone, valid_from, valid_to, status, version, created_at, updated_at)
                        VALUES (?, ?, ?, 'Europe/Madrid', DATE '2026-06-01', DATE '2026-05-01', 'ACTIVE', 0, now(), now())
                        """,
                        UUID.randomUUID(),
                        tenantId,
                        "Vigencia invalida"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsIncoherentWeeklyRule() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Calendar E");
        UUID calendarId = UUID.randomUUID();
        insertCalendar(jdbc, calendarId, tenantId, "Reglas");

        // Un dia no laborable con minutos esperados viola ck_calendar_day_rule_coherent.
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO calendar_day_rule (calendar_id, day_of_week, working, expected_minutes)"
                                + " VALUES (?, 'SUNDAY', false, 120)",
                        calendarId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Y un dia laborable sin minutos, tambien.
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO calendar_day_rule (calendar_id, day_of_week, working, expected_minutes)"
                                + " VALUES (?, 'MONDAY', true, 0)",
                        calendarId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateHolidayDateInTheSameCalendar() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Calendar F");
        UUID calendarId = UUID.randomUUID();
        insertCalendar(jdbc, calendarId, tenantId, "Festivos");

        jdbc.update(
                "INSERT INTO calendar_holiday (calendar_id, holiday_date, name) VALUES (?, ?, 'Reyes')",
                calendarId,
                LocalDate.of(2026, 1, 6));

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO calendar_holiday (calendar_id, holiday_date, name) VALUES (?, ?, 'Repetido')",
                        calendarId,
                        LocalDate.of(2026, 1, 6)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSecondAssignmentForTheSameScopeAndTarget() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Calendar G");
        UUID calendarId = UUID.randomUUID();
        insertCalendar(jdbc, calendarId, tenantId, "Asignable");
        UUID employeeId = UUID.randomUUID();

        insertAssignment(jdbc, UUID.randomUUID(), tenantId, calendarId, "EMPLOYEE", employeeId);

        assertThatThrownBy(() ->
                        insertAssignment(jdbc, UUID.randomUUID(), tenantId, calendarId, "EMPLOYEE", employeeId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSecondTenantWideAssignmentEvenThoughTargetIsNull() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Calendar H");
        UUID calendarId = UUID.randomUUID();
        insertCalendar(jdbc, calendarId, tenantId, "Por defecto");

        insertAssignment(jdbc, UUID.randomUUID(), tenantId, calendarId, "TENANT", null);

        // Un UNIQUE ordinario no deduplicaria los NULL: hace falta el indice parcial.
        assertThatThrownBy(() -> insertAssignment(jdbc, UUID.randomUUID(), tenantId, calendarId, "TENANT", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAssignmentWithInconsistentScopeAndTarget() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Calendar I");
        UUID calendarId = UUID.randomUUID();
        insertCalendar(jdbc, calendarId, tenantId, "Coherencia");

        assertThatThrownBy(() ->
                        insertAssignment(jdbc, UUID.randomUUID(), tenantId, calendarId, "TENANT", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertAssignment(jdbc, UUID.randomUUID(), tenantId, calendarId, "TEAM", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void archivingCalendarRowsCascadesToChildrenOnDelete() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Calendar J");
        UUID calendarId = UUID.randomUUID();
        insertCalendar(jdbc, calendarId, tenantId, "Cascada");
        jdbc.update(
                "INSERT INTO calendar_holiday (calendar_id, holiday_date, name) VALUES (?, ?, 'Reyes')",
                calendarId,
                LocalDate.of(2026, 1, 6));
        insertAssignment(jdbc, UUID.randomUUID(), tenantId, calendarId, "TENANT", null);

        jdbc.update("DELETE FROM work_calendar WHERE id = ?", calendarId);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM calendar_holiday WHERE calendar_id = ?", Integer.class, calendarId))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM calendar_assignment WHERE calendar_id = ?", Integer.class, calendarId))
                .isZero();
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, table, null)) {
            return resultSet.next();
        }
    }

    private UUID insertTenant(JdbcTemplate jdbc, String name) {
        UUID tenantId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO tenant (id, name, status, timezone, created_at, updated_at, activated_at)
                VALUES (?, ?, 'ACTIVE', 'Europe/Madrid', now(), now(), now())
                """,
                tenantId,
                name + " " + tenantId);
        return tenantId;
    }

    private void insertCalendar(JdbcTemplate jdbc, UUID id, UUID tenantId, String name) {
        jdbc.update(
                """
                INSERT INTO work_calendar
                    (id, tenant_id, name, timezone, valid_from, valid_to, status, version, created_at, updated_at)
                VALUES (?, ?, ?, 'Europe/Madrid', DATE '2026-01-01', NULL, 'ACTIVE', 0, now(), now())
                """,
                id,
                tenantId,
                name);
    }

    private void insertAssignment(
            JdbcTemplate jdbc, UUID id, UUID tenantId, UUID calendarId, String scope, UUID targetId) {
        jdbc.update(
                """
                INSERT INTO calendar_assignment
                    (id, tenant_id, calendar_id, scope, scope_target_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, now(), now())
                """,
                id,
                tenantId,
                calendarId,
                scope,
                targetId);
    }
}
