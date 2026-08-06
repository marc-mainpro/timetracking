package com.tfp.timetracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
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

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlywayAbsenceMigrationIntegrationTest {

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
    void appliesAbsenceMigrationFromEmptyDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "absence_type")).isTrue();
            assertThat(tableExists(connection, "absence_request")).isTrue();
        }
    }

    @Test
    void rejectsDuplicateAbsenceTypeCodeInSameTenant() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Absence A");

        insertAbsenceType(jdbc, UUID.randomUUID(), tenantId, "VAC");

        assertThatThrownBy(() -> insertAbsenceType(jdbc, UUID.randomUUID(), tenantId, "VAC"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvertedAbsenceRequestRange() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Absence B");
        UUID userId = insertUser(jdbc, tenantId, "absence@example.com");
        UUID typeId = insertAbsenceType(jdbc, UUID.randomUUID(), tenantId, "VAC");

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO absence_request (id, tenant_id, employee_id, absence_type_id, start_date, end_date, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        tenantId,
                        userId,
                        typeId,
                        LocalDate.of(2026, 8, 12),
                        LocalDate.of(2026, 8, 10),
                        "PENDING",
                        Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static UUID insertTenant(JdbcTemplate jdbc, String name) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO tenant (id, name, status, timezone, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, name, "ACTIVE", "Europe/Madrid", now, now);
        return id;
    }

    private static UUID insertUser(JdbcTemplate jdbc, UUID tenantId, String email) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO app_user (id, tenant_id, email, password_hash, first_name, last_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, email, "hash", "First", "Last", "ACTIVE", now, now);
        return id;
    }

    private static UUID insertAbsenceType(JdbcTemplate jdbc, UUID id, UUID tenantId, String code) {
        jdbc.update(
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

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"})) {
            return resultSet.next();
        }
    }
}
