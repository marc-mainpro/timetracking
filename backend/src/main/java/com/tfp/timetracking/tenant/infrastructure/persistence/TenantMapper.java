package com.tfp.timetracking.tenant.infrastructure.persistence;

import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantStatus;

/**
 * Mapper dominio &lt;-&gt; JPA para Tenant (CONTEXT-GLOBAL §4: entidades JPA
 * separadas del modelo de dominio).
 */
final class TenantMapper {

    private TenantMapper() {}

    static TenantJpaEntity toJpaEntity(Tenant tenant) {
        return new TenantJpaEntity(
                tenant.id(),
                tenant.name(),
                tenant.status().name(),
                tenant.timezone(),
                tenant.createdAt(),
                tenant.updatedAt());
    }

    static Tenant toDomain(TenantJpaEntity entity) {
        return Tenant.reconstitute(
                entity.getId(),
                entity.getName(),
                TenantStatus.valueOf(entity.getStatus()),
                entity.getTimezone(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
