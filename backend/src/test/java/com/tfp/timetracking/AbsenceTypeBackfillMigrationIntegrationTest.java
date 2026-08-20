package com.tfp.timetracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.support.SharedPostgresContainerSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V28 repara los tenants que se quedaron sin catálogo de tipos de ausencia
 * (RF-ABS-001).
 *
 * <p>No basta con el fix de {@code SeedDefaultAbsenceTypesListener}: los tenants
 * creados por alta pública mientras el listener sembraba sobre el tenant del
 * envelope ya están en producción con el catálogo vacío, y no hay ningún
 * endpoint que lo rellene.
 *
 * <p>Migra deliberadamente hasta V27 primero, prepara ese estado roto y solo
 * entonces aplica V28: es la única forma de ejercitar el backfill, porque la
 * clase base de migraciones aplica siempre el esquema completo.
 */
class AbsenceTypeBackfillMigrationIntegrationTest extends SharedPostgresContainerSupport {

    private static final UUID PLATFORM_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final List<String> DEFAULT_CODES =
            List.of("VACACIONES", "PERMISO", "BAJA", "JUSTIFICADA", "NO_JUSTIFICADA");

    private String schema;
    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateUpToTheVersionBeforeTheBackfill(TestInfo testInfo) {
        schema = "fw_backfill_"
                + Integer.toHexString(testInfo.getTestMethod().map(method -> method.getName()).orElse("test").hashCode());
        new JdbcTemplate(newDataSource(null)).execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");

        dataSource = newDataSource(schema);
        flyway().target("27").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void seedsTheDefaultCatalogueForTenantsThatHaveNone() {
        UUID affected = insertTenant("Sin catálogo");
        UUID withCatalogue = insertTenant("Con catálogo");
        UUID ownTypeId = insertAbsenceType(withCatalogue, "PROPIO");

        applyBackfill();

        assertThat(codesOf(affected)).containsExactlyInAnyOrderElementsOf(DEFAULT_CODES);
        assertThat(jdbc.queryForObject(
                        "SELECT requires_approval FROM absence_type WHERE tenant_id = ? AND code = 'NO_JUSTIFICADA'",
                        Boolean.class,
                        affected))
                .isFalse();
        assertThat(jdbc.queryForObject(
                        "SELECT requires_approval FROM absence_type WHERE tenant_id = ? AND code = 'VACACIONES'",
                        Boolean.class,
                        affected))
                .isTrue();

        // Un tenant que ya tiene su catálogo no se toca: podría estar
        // personalizado.
        assertThat(codesOf(withCatalogue)).containsExactly("PROPIO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM absence_type WHERE id = ?", Integer.class, ownTypeId))
                .isEqualTo(1);
    }

    @Test
    void removesTheCatalogueThatTheBugLeftOnThePlatformTenant() {
        DEFAULT_CODES.forEach(code -> insertAbsenceType(PLATFORM_TENANT_ID, code));

        applyBackfill();

        assertThat(codesOf(PLATFORM_TENANT_ID)).isEmpty();
    }

    @Test
    void keepsPlatformTypesThatAreAlreadyReferencedByARequest() {
        // Borrarlos rompería fk_absence_request_type: se prefiere dejar el dato
        // sucio antes que fallar la migración.
        UUID typeId = insertAbsenceType(PLATFORM_TENANT_ID, "VACACIONES");
        insertAbsenceRequest(PLATFORM_TENANT_ID, typeId);

        applyBackfill();

        assertThat(codesOf(PLATFORM_TENANT_ID)).containsExactly("VACACIONES");
    }

    @Test
    void doesNotDuplicateWhenTheWholeSchemaIsMigratedTwice() {
        UUID affected = insertTenant("Sin catálogo");

        applyBackfill();
        flyway().load().migrate();

        assertThat(codesOf(affected)).containsExactlyInAnyOrderElementsOf(DEFAULT_CODES);
    }

    private void applyBackfill() {
        flyway().target("28").load().migrate();
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .cleanDisabled(false);
    }

    private List<String> codesOf(UUID tenantId) {
        return jdbc.queryForList("SELECT code FROM absence_type WHERE tenant_id = ?", String.class, tenantId);
    }

    private UUID insertTenant(String name) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO tenant (id, name, status, timezone, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, name, "ACTIVE", "Europe/Madrid", now, now);
        return id;
    }

    private UUID insertAbsenceType(UUID tenantId, String code) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO absence_type (id, tenant_id, code, name, requires_approval, allows_attachment, active) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, code, code, true, false, true);
        return id;
    }

    private void insertAbsenceRequest(UUID tenantId, UUID absenceTypeId) {
        UUID userId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO app_user (id, tenant_id, email, password_hash, first_name, last_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId, tenantId, "backfill-" + userId + "@example.com", "hash", "First", "Last", "ACTIVE", now, now);
        jdbc.update(
                "INSERT INTO absence_request (id, tenant_id, employee_id, absence_type_id, start_date, end_date, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                userId,
                absenceTypeId,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                "PENDING",
                now);
    }
}
