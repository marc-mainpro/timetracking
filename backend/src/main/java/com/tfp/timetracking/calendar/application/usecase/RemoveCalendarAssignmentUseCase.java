package com.tfp.timetracking.calendar.application.usecase;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignment;
import com.tfp.timetracking.calendar.domain.repository.CalendarAssignmentRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retira una asignacion de calendario (RF-CAL-006, T70-04).
 *
 * <p>A diferencia del calendario, la asignacion si se borra fisicamente: no
 * tiene valor historico propio —el hecho queda registrado en el evento de
 * integracion {@code calendar.calendar-assignment-removed.v1} y en auditoria— y
 * conservarla como fila "inactiva" complicaria la unicidad por ambito.
 */
@Service
public class RemoveCalendarAssignmentUseCase {

    private final CalendarAssignmentRepository assignmentRepository;
    private final TenantContext tenantContext;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditRecorder auditRecorder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public RemoveCalendarAssignmentUseCase(
            CalendarAssignmentRepository assignmentRepository,
            TenantContext tenantContext,
            DomainEventPublisher domainEventPublisher,
            AuditRecorder auditRecorder,
            Clock clock,
            IdGenerator idGenerator) {
        this.assignmentRepository = assignmentRepository;
        this.tenantContext = tenantContext;
        this.domainEventPublisher = domainEventPublisher;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public void remove(UUID assignmentId) {
        UUID tenantId = tenantContext.currentTenantId();
        CalendarAssignment assignment = assignmentRepository
                .findById(tenantId, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Asignacion de calendario no encontrada"));

        assignment.remove(clock, idGenerator);
        assignmentRepository.delete(tenantId, assignmentId);
        domainEventPublisher.publish(assignment.pullDomainEvents());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("calendarId", assignment.calendarId().toString());
        metadata.put("scope", assignment.scope().name());
        if (assignment.targetId() != null) {
            metadata.put("targetId", assignment.targetId().toString());
        }
        auditRecorder.record("CALENDAR_ASSIGNMENT_REMOVED", "CalendarAssignment", assignmentId, metadata);
    }
}
