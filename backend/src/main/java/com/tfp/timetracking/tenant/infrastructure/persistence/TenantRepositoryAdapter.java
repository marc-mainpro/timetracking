package com.tfp.timetracking.tenant.infrastructure.persistence;

import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import com.tfp.timetracking.tenant.domain.TenantStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @Override
    public PagedResult<Tenant> findAllExcluding(UUID excludedId, TenantStatus status, String name, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TenantJpaEntity> result;
        if (status != null && name != null) {
            result = jpaRepository.findByIdNotAndStatusAndNameContainingIgnoreCase(excludedId, status.name(), name, pageable);
        } else if (status != null) {
            result = jpaRepository.findByIdNotAndStatus(excludedId, status.name(), pageable);
        } else if (name != null) {
            result = jpaRepository.findByIdNotAndNameContainingIgnoreCase(excludedId, name, pageable);
        } else {
            result = jpaRepository.findByIdNot(excludedId, pageable);
        }
        return new PagedResult<>(
                result.getContent().stream().map(TenantMapper::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
