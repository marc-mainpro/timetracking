package com.tfp.timetracking.calendar.infrastructure.persistence;

import com.tfp.timetracking.calendar.domain.model.CalendarStatus;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import com.tfp.timetracking.calendar.domain.repository.WorkCalendarRepository;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * Adaptador JPA del puerto {@link WorkCalendarRepository}.
 *
 * <p>Todas las consultas filtran por {@code tenant_id} en la propia sentencia
 * (ADR-0002): un id de otro tenant devuelve vacio, nunca el agregado ajeno, para
 * que el caso de uso responda 404 sin revelar su existencia.
 */
@Repository
public class WorkCalendarRepositoryAdapter implements WorkCalendarRepository {

    private final WorkCalendarJpaRepository jpaRepository;

    public WorkCalendarRepositoryAdapter(WorkCalendarJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public WorkCalendar save(WorkCalendar calendar) {
        return WorkCalendarMapper.toDomain(jpaRepository.save(WorkCalendarMapper.toJpaEntity(calendar)));
    }

    @Override
    public Optional<WorkCalendar> findById(UUID tenantId, UUID id) {
        return jpaRepository.findByTenantIdAndId(tenantId, id).map(WorkCalendarMapper::toDomain);
    }

    @Override
    public List<WorkCalendar> findAllByIds(UUID tenantId, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
                .map(WorkCalendarMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByName(UUID tenantId, String name) {
        return jpaRepository.existsByTenantIdAndName(tenantId, name);
    }

    @Override
    public PagedResult<WorkCalendar> findByTenant(UUID tenantId, CalendarStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        Page<WorkCalendarJpaEntity> result = status == null
                ? jpaRepository.findByTenantId(tenantId, pageable)
                : jpaRepository.findByTenantIdAndStatus(tenantId, status.name(), pageable);
        return new PagedResult<>(
                result.getContent().stream().map(WorkCalendarMapper::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
