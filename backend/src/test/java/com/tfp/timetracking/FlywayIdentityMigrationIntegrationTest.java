package com.tfp.timetracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.support.AbstractFlywayMigrationTest;
import java.sql.Connection;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T201: verifica que V2__identity.sql se aplica limpio desde una base de
 * datos vacia (via Flyway al arrancar el contexto de Spring) y que el esquema
 * final deja el email de usuario como unico global (ADR-0008), requisito
 * necesario para el login por {@code email + password} sin ambiguedad.
 */
class FlywayIdentityMigrationIntegrationTest extends AbstractFlywayMigrationTest {

    @Test
    void appliesIdentityMigrationFromEmptyDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "tenant")).isTrue();
            assertThat(tableExists(connection, "app_user")).isTrue();
            assertThat(tableExists(connection, "user_role")).isTrue();
            assertThat(tableExists(connection, "refresh_token")).isTrue();
            assertThat(tableExists(connection, "user_session")).isTrue();
        }
    }

    @Test
    void refreshTokensReferenceSessions() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tenantId = insertTenant(jdbc, "Tenant sessions");
        UUID userId = insertUserReturningId(jdbc, tenantId, "sessions@example.com");
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        assertThatCode(() -> jdbc.update(
                        "INSERT INTO user_session (id, user_id, tenant_id, created_at, last_used_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                        sessionId,
                        userId,
                        tenantId,
                        Timestamp.from(now),
                        Timestamp.from(now),
                        Timestamp.from(now.plusSeconds(60))))
                .doesNotThrowAnyException();

        assertThatCode(() -> jdbc.update(
                        "INSERT INTO refresh_token (id, user_id, session_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        userId,
                        sessionId,
                        "hash-1",
                        Timestamp.from(now.plusSeconds(60)),
                        Timestamp.from(now)))
                .doesNotThrowAnyException();
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
        insertUserReturningId(jdbc, tenantId, email);
    }

    private static UUID insertUserReturningId(JdbcTemplate jdbc, UUID tenantId, String email) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO app_user (id, tenant_id, email, password_hash, first_name, "
                        + "last_name, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, email, "hash", "First", "Last", "ACTIVE", Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet resultSet =
                connection.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"})) {
            return resultSet.next();
        }
    }
}
