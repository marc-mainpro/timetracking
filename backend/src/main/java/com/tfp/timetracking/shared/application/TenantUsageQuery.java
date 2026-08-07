package com.tfp.timetracking.shared.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Datos de uso que la administracion de plataforma muestra junto a cada tenant
 * (RF-TEN-001): cuantos usuarios tiene y cuando se accedio por ultima vez.
 *
 * <p>Los conoce {@code identity} —es quien tiene usuarios y sesiones— y los
 * consume {@code tenant}, que no accede al repositorio de otro modulo
 * (AGENTS.md, ADR-0001).
 *
 * <p>El puerto vive en {@code shared} y no en {@code tenant} porque
 * {@code tenant} <b>ya depende de</b> {@code identity}: su caso de uso de alta
 * crea el primer administrador. Declararlo en {@code tenant} obligaria a
 * {@code identity} a mirar hacia {@code tenant} para implementarlo y cerraria
 * un ciclo entre ambos modulos, que es justo lo que prohibe
 * {@code ModuleCyclesTest}. Es el mismo criterio por el que
 * {@code IntegrationEventMapper} vive en {@code shared} (ADR-0011).
 *
 * <p>La consulta es <b>por lote</b> y no tenant a tenant: el listado es
 * paginado y resolverlo con una llamada por fila daria una consulta por tenant
 * en cada carga de pagina.
 */
public interface TenantUsageQuery {

    /**
     * @param tenantIds tenants de la pagina actual
     * @return uso por tenant; un tenant sin usuarios puede no aparecer en el mapa
     */
    Map<UUID, TenantUsage> findUsage(Collection<UUID> tenantIds);

    /**
     * @param userCount numero de usuarios del tenant, activos o no
     * @param lastAccessAt ultimo uso de una sesion del tenant, o {@code null} si
     *     nunca se ha accedido
     */
    record TenantUsage(long userCount, Instant lastAccessAt) {

        public static final TenantUsage NONE = new TenantUsage(0, null);
    }
}
