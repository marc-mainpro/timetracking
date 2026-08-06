package com.tfp.timetracking.absence.infrastructure.persistence;

import com.tfp.timetracking.absence.domain.AbsenceRequest;
import com.tfp.timetracking.absence.domain.AbsenceRequestRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class AbsenceRequestRepositoryAdapter implements AbsenceRequestRepository {

    private final AbsenceRequestJpaRepository jpaRepository;

    public AbsenceRequestRepositoryAdapter(AbsenceRequestJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AbsenceRequest save(AbsenceRequest request) {
        return AbsenceRequestMapper.toDomain(jpaRepository.save(AbsenceRequestMapper.toJpaEntity(request)));
    }

    @Override
    public Optional<AbsenceRequest> findById(UUID tenantId, UUID requestId) {
        return jpaRepository.findByTenantIdAndId(tenantId, requestId).map(AbsenceRequestMapper::toDomain);
    }

    @Override
    public List<AbsenceRequest> findByEmployeeAndDateRange(UUID tenantId, UUID employeeId, LocalDate from, LocalDate to) {
        return jpaRepository.findByEmployeeAndDateRange(tenantId, employeeId, from, to).stream()
                .map(AbsenceRequestMapper::toDomain)
                .toList();
    }

    @Override
    public List<AbsenceRequest> findByTenantAndDateRange(UUID tenantId, LocalDate from, LocalDate to) {
        return jpaRepository.findByTenantAndDateRange(tenantId, from, to).stream()
                .map(AbsenceRequestMapper::toDomain)
                .toList();
    }
}
