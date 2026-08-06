package com.tfp.timetracking.identity.infrastructure.persistence;

import com.tfp.timetracking.shared.application.TenantUsageQuery;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Implementacion en {@code identity} del puerto que declara {@code tenant}
 * (RF-TEN-001).
 *
 * <p>Resuelve la pagina entera en <b>dos consultas agregadas</b>, una por tabla,
 * en lugar de una por tenant. Se usa JDBC y no JPA porque no hay agregado que
 * cargar: son dos recuentos y un maximo, y mapearlos a entidades solo anadiria
 * trabajo para descartarlo despues.
 *
 * <p>El ultimo acceso se toma de {@code user_session.last_used_at} incluyendo
 * las sesiones revocadas o caducadas: la pregunta es cuando se uso el tenant
 * por ultima vez, no si esa sesion sigue viva. Excluirlas haria que un tenant
 * activo pareciera inactivo en cuanto expirasen sus sesiones.
 */
@Repository
public class JdbcTenantUsageQuery implements TenantUsageQuery {

    private static final String USER_COUNT_SQL =
            "SELECT tenant_id, count(*) AS total FROM app_user WHERE tenant_id IN (:tenantIds) GROUP BY tenant_id";

    private static final String LAST_ACCESS_SQL =
            "SELECT tenant_id, max(last_used_at) AS last_access FROM user_session "
                    + "WHERE tenant_id IN (:tenantIds) GROUP BY tenant_id";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTenantUsageQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<UUID, TenantUsage> findUsage(Collection<UUID> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource("tenantIds", List.copyOf(tenantIds));

        Map<UUID, Long> userCounts = new HashMap<>();
        jdbcTemplate.query(USER_COUNT_SQL, parameters, resultSet -> {
            userCounts.put(resultSet.getObject("tenant_id", UUID.class), resultSet.getLong("total"));
        });

        Map<UUID, java.time.Instant> lastAccesses = new HashMap<>();
        jdbcTemplate.query(LAST_ACCESS_SQL, parameters, resultSet -> {
            java.sql.Timestamp lastAccess = resultSet.getTimestamp("last_access");
            if (lastAccess != null) {
                lastAccesses.put(resultSet.getObject("tenant_id", UUID.class), lastAccess.toInstant());
            }
        });

        Map<UUID, TenantUsage> usage = new HashMap<>();
        for (UUID tenantId : tenantIds) {
            usage.put(
                    tenantId,
                    new TenantUsage(userCounts.getOrDefault(tenantId, 0L), lastAccesses.get(tenantId)));
        }
        return Map.copyOf(usage);
    }
}
