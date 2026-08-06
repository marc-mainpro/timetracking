package com.tfp.timetracking.shift.infrastructure.persistence;

import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ShiftTemplateRepositoryAdapter {

    private final ShiftTemplateJpaRepository jpaRepository;

    public ShiftTemplateRepositoryAdapter(ShiftTemplateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    public ShiftTemplate save(ShiftTemplate template) {
        return ShiftTemplateMapper.toDomain(jpaRepository.save(ShiftTemplateMapper.toJpaEntity(template)));
    }

    public Optional<ShiftTemplate> findById(UUID tenantId, UUID id) {
        return jpaRepository.findByTenantIdAndId(tenantId, id).map(ShiftTemplateMapper::toDomain);
    }

    public Optional<ShiftTemplate> findByName(UUID tenantId, String name) {
        return jpaRepository.findByTenantIdAndName(tenantId, name).map(ShiftTemplateMapper::toDomain);
    }

    public List<ShiftTemplate> findByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantIdOrderByNameAsc(tenantId).stream().map(ShiftTemplateMapper::toDomain).toList();
    }
}
