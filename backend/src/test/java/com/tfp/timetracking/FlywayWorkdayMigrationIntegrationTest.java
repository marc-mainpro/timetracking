package com.tfp.timetracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
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
class FlywayWorkdayMigrationIntegrationTest {

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
    void appliesWorkdayMigrationFromEmptyDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "workday")).isTrue();
            assertThat(tableExists(connection, "break_entry")).isTrue();
        }
    }

    @Test
    void rejectsSecondActiveWorkdayForSameTenantAndEmployee() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Tenant A");
        UUID employeeId = insertUser(jdbc, tenantId, "worker-a@example.com");

        insertWorkday(jdbc, UUID.randomUUID(), tenantId, employeeId, "OPEN");

        assertThatThrownBy(() -> insertWorkday(jdbc, UUID.randomUUID(), tenantId, employeeId, "ON_BREAK"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSecondOpenBreakForSameWorkday() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Tenant B");
        UUID employeeId = insertUser(jdbc, tenantId, "worker-b@example.com");
        UUID workdayId = UUID.randomUUID();
        insertWorkday(jdbc, workdayId, tenantId, employeeId, "OPEN");
        insertBreak(jdbc, UUID.randomUUID(), workdayId, Timestamp.from(Instant.now()), null);

        assertThatThrownBy(() -> insertBreak(jdbc, UUID.randomUUID(), workdayId, Timestamp.from(Instant.now()), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static UUID insertTenant(JdbcTemplate jdbc, String name) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO tenant (id, name, status, timezone, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, name, "ACTIVE", "Europe/Madrid", Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private static UUID insertUser(JdbcTemplate jdbc, UUID tenantId, String email) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO app_user (id, tenant_id, email, password_hash, first_name, last_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, email, "hash", "First", "Last", "ACTIVE", Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private static void insertWorkday(JdbcTemplate jdbc, UUID workdayId, UUID tenantId, UUID employeeId, String status) {
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO workday (id, tenant_id, employee_id, status, started_at, ended_at, version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                workdayId,
                tenantId,
                employeeId,
                status,
                Timestamp.from(now),
                status.equals("CLOSED") ? Timestamp.from(now.plusSeconds(60)) : null,
                0L,
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private static void insertBreak(JdbcTemplate jdbc, UUID breakId, UUID workdayId, Timestamp startedAt, Timestamp endedAt) {
        jdbc.update(
                "INSERT INTO break_entry (id, workday_id, started_at, ended_at) VALUES (?, ?, ?, ?)",
                breakId,
                workdayId,
                startedAt,
                endedAt);
    }

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"})) {
            return resultSet.next();
        }
    }
}
