package com.tfp.timetracking.timetracking.infrastructure.persistence;

import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class WorkdayRepositoryAdapter implements WorkdayRepository {

    private final WorkdayJpaRepository jpaRepository;

    public WorkdayRepositoryAdapter(WorkdayJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Workday save(Workday workday) {
        return WorkdayMapper.toDomain(jpaRepository.save(WorkdayMapper.toJpaEntity(workday)));
    }

    @Override
    public Optional<Workday> findById(UUID tenantId, UUID id) {
        return jpaRepository.findByTenantIdAndId(tenantId, id).map(WorkdayMapper::toDomain);
    }

    @Override
    public Optional<Workday> findActiveByEmployee(UUID tenantId, UUID employeeId) {
        return jpaRepository.findActiveByEmployee(tenantId, employeeId).map(WorkdayMapper::toDomain);
    }
}
