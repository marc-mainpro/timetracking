package com.tfp.timetracking.timetracking.infrastructure.persistence;

import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class WorkdayRepositoryAdapter implements WorkdayRepository {

    private static final Instant MIN_FILTER_DATE = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX_FILTER_DATE = Instant.parse("9999-12-31T23:59:59Z");

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

    @Override
    public PagedResult<Workday> findByEmployee(UUID tenantId, UUID employeeId, Instant from, Instant to, int page, int size) {
        return toPagedResult(jpaRepository.findByEmployee(
                tenantId,
                employeeId,
                effectiveFrom(from),
                effectiveTo(to),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"))));
    }

    @Override
    public PagedResult<Workday> findByTenant(UUID tenantId, UUID employeeId, Instant from, Instant to, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        return toPagedResult(employeeId == null
                ? jpaRepository.findByTenant(tenantId, effectiveFrom(from), effectiveTo(to), pageRequest)
                : jpaRepository.findByTenantAndEmployee(tenantId, employeeId, effectiveFrom(from), effectiveTo(to), pageRequest));
    }

    private PagedResult<Workday> toPagedResult(Page<WorkdayJpaEntity> page) {
        return new PagedResult<>(
                page.getContent().stream().map(WorkdayMapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private Instant effectiveFrom(Instant from) {
        return from != null ? from : MIN_FILTER_DATE;
    }

    private Instant effectiveTo(Instant to) {
        return to != null ? to : MAX_FILTER_DATE;
    }
}
