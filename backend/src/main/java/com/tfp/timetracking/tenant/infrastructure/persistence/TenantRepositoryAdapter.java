package com.tfp.timetracking.tenant.infrastructure.persistence;

import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de infraestructura que implementa el puerto
 * {@link TenantRepository} usando Spring Data JPA (CONTEXT-GLOBAL §4:
 * infrastructure implementa puertos definidos en domain).
 */
@Repository
public class TenantRepositoryAdapter implements TenantRepository {

    private final TenantJpaRepository jpaRepository;

    public TenantRepositoryAdapter(TenantJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Tenant save(Tenant tenant) {
        TenantJpaEntity saved = jpaRepository.save(TenantMapper.toJpaEntity(tenant));
        return TenantMapper.toDomain(saved);
    }

    @Override
    public Optional<Tenant> findById(UUID id) {
        return jpaRepository.findById(id).map(TenantMapper::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }
}
