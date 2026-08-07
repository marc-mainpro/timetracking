package com.tfp.timetracking.shift.infrastructure.persistence;

import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ShiftTemplateRepositoryAdapter implements ShiftTemplateRepository {

    private final ShiftTemplateJpaRepository jpaRepository;

    public ShiftTemplateRepositoryAdapter(ShiftTemplateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ShiftTemplate save(ShiftTemplate template) {
        return ShiftTemplateMapper.toDomain(jpaRepository.save(ShiftTemplateMapper.toJpaEntity(template)));
    }

    @Override
    public Optional<ShiftTemplate> findById(UUID tenantId, UUID id) {
        return jpaRepository.findByTenantIdAndId(tenantId, id).map(ShiftTemplateMapper::toDomain);
    }

    @Override
    public Optional<ShiftTemplate> findByName(UUID tenantId, String name) {
        return jpaRepository.findByTenantIdAndName(tenantId, name).map(ShiftTemplateMapper::toDomain);
    }

    @Override
    public List<ShiftTemplate> findByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantIdOrderByNameAsc(tenantId).stream().map(ShiftTemplateMapper::toDomain).toList();
    }
}
