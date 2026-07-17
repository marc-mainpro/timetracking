package com.tfp.timetracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Timestamp;
import java.sql.ResultSet;
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

/**
 * T201: verifica que V2__identity.sql se aplica limpio desde una base de
 * datos vacia (via Flyway al arrancar el contexto de Spring) y que el esquema
 * final deja el email de usuario como unico global (ADR-0008), requisito
 * necesario para el login por {@code email + password} sin ambiguedad.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlywayIdentityMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
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
    void appliesIdentityMigrationFromEmptyDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "tenant")).isTrue();
            assertThat(tableExists(connection, "app_user")).isTrue();
            assertThat(tableExists(connection, "user_role")).isTrue();
            assertThat(tableExists(connection, "refresh_token")).isTrue();
        }
    }

    @Test
    void rejectsSameEmailAcrossDifferentTenants() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantA = insertTenant(jdbc, "Tenant A");
        UUID tenantB = insertTenant(jdbc, "Tenant B");

        insertUser(jdbc, tenantA, "shared@example.com");

        assertThatThrownBy(() -> insertUser(jdbc, tenantB, "shared@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSameEmailWithinSameTenant() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantA = insertTenant(jdbc, "Tenant C");

        insertUser(jdbc, tenantA, "duplicate@example.com");

        assertThatThrownBy(() -> insertUser(jdbc, tenantA, "duplicate@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static UUID insertTenant(JdbcTemplate jdbc, String name) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO tenant (id, name, status, timezone, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, name, "ACTIVE", "Europe/Madrid", Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private static void insertUser(JdbcTemplate jdbc, UUID tenantId, String email) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO app_user (id, tenant_id, email, password_hash, first_name, "
                        + "last_name, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, email, "hash", "First", "Last", "ACTIVE", Timestamp.from(now), Timestamp.from(now));
    }

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet resultSet =
                connection.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"})) {
            return resultSet.next();
        }
    }
}
