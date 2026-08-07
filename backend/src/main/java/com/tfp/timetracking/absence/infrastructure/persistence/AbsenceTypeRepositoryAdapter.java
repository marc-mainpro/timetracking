package com.tfp.timetracking.absence.infrastructure.persistence;

import com.tfp.timetracking.absence.domain.AbsenceType;
import com.tfp.timetracking.absence.domain.AbsenceTypeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class AbsenceTypeRepositoryAdapter implements AbsenceTypeRepository {

    private final AbsenceTypeJpaRepository jpaRepository;

    public AbsenceTypeRepositoryAdapter(AbsenceTypeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AbsenceType save(AbsenceType absenceType) {
        return AbsenceTypeMapper.toDomain(jpaRepository.save(AbsenceTypeMapper.toJpaEntity(absenceType)));
    }

    @Override
    public Optional<AbsenceType> findById(UUID tenantId, UUID absenceTypeId) {
        return jpaRepository.findByTenantIdAndId(tenantId, absenceTypeId).map(AbsenceTypeMapper::toDomain);
    }

    @Override
    public Optional<AbsenceType> findByCode(UUID tenantId, String code) {
        return jpaRepository.findByTenantIdAndCode(tenantId, code).map(AbsenceTypeMapper::toDomain);
    }

    @Override
    public List<AbsenceType> findByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantIdOrderByCodeAsc(tenantId).stream().map(AbsenceTypeMapper::toDomain).toList();
    }
}
