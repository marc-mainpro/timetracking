package com.tfp.timetracking.tenant.application;

import com.tfp.timetracking.tenant.domain.Tenant;
import java.time.Instant;

/**
 * Tenant enriquecido con sus datos de uso para el listado de plataforma
 * (RF-TEN-001).
 *
 * <p>El uso no vive en el agregado {@link Tenant} a proposito: el numero de
 * usuarios y el ultimo acceso son hechos de otro modulo, cambian
 * constantemente y no participan en ninguna invariante del ciclo de vida del
 * tenant. Meterlos dentro obligaria a mantenerlos sincronizados en el agregado
 * a cada login.
 *
 * @param lastAccessAt {@code null} si nunca se ha accedido a ese tenant
 */
public record TenantSummary(Tenant tenant, long userCount, Instant lastAccessAt) {}
