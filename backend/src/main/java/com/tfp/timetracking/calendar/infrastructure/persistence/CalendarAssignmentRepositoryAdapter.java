package com.tfp.timetracking.calendar.infrastructure.persistence;

import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignment;
import com.tfp.timetracking.calendar.domain.repository.CalendarAssignmentRepository;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link CalendarAssignmentRepository}. */
@Repository
public class CalendarAssignmentRepositoryAdapter implements CalendarAssignmentRepository {

    private final CalendarAssignmentJpaRepository jpaRepository;

    public CalendarAssignmentRepositoryAdapter(CalendarAssignmentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CalendarAssignment save(CalendarAssignment assignment) {
        return CalendarAssignmentMapper.toDomain(
                jpaRepository.save(CalendarAssignmentMapper.toJpaEntity(assignment)));
    }

    @Override
    public Optional<CalendarAssignment> findById(UUID tenantId, UUID id) {
        return jpaRepository.findByTenantIdAndId(tenantId, id).map(CalendarAssignmentMapper::toDomain);
    }

    @Override
    public Optional<CalendarAssignment> findByScope(UUID tenantId, AssignmentScope scope, UUID targetId) {
        // Derived query distinta para el caso TENANT: en SQL "columna = NULL"
        // nunca es cierto, asi que hace falta el "IS NULL" explicito.
        Optional<CalendarAssignmentJpaEntity> entity = targetId == null
                ? jpaRepository.findByTenantIdAndScopeAndScopeTargetIdIsNull(tenantId, scope.name())
                : jpaRepository.findByTenantIdAndScopeAndScopeTargetId(tenantId, scope.name(), targetId);
        return entity.map(CalendarAssignmentMapper::toDomain);
    }

    @Override
    public List<CalendarAssignment> findApplicable(UUID tenantId, UUID employeeId, UUID teamId) {
        return jpaRepository.findApplicable(tenantId, employeeId, teamId).stream()
                .map(CalendarAssignmentMapper::toDomain)
                .toList();
    }

    @Override
    public PagedResult<CalendarAssignment> findByTenant(UUID tenantId, UUID calendarId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "scope", "createdAt"));
        Page<CalendarAssignmentJpaEntity> result = calendarId == null
                ? jpaRepository.findByTenantId(tenantId, pageable)
                : jpaRepository.findByTenantIdAndCalendarId(tenantId, calendarId, pageable);
        return new PagedResult<>(
                result.getContent().stream()
                        .map(CalendarAssignmentMapper::toDomain)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public void delete(UUID tenantId, UUID id) {
        // Se localiza primero por (tenant, id) para no borrar nunca una fila de
        // otro tenant aunque llegue un id valido de otra organizacion.
        jpaRepository.findByTenantIdAndId(tenantId, id).ifPresent(jpaRepository::delete);
    }
}
