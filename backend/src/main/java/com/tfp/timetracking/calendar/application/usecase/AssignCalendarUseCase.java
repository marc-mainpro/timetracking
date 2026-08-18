package com.tfp.timetracking.calendar.application.usecase;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.calendar.application.command.AssignCalendarCommand;
import com.tfp.timetracking.calendar.domain.model.CalendarArchivedException;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignment;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignmentAlreadyExistsException;
import com.tfp.timetracking.calendar.domain.model.CalendarStatus;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import com.tfp.timetracking.calendar.domain.repository.CalendarAssignmentRepository;
import com.tfp.timetracking.calendar.domain.repository.WorkCalendarRepository;
import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import com.tfp.timetracking.shared.application.EmployeeAssignmentTargetQuery;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.TargetNotEmployeeException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asigna un calendario a un ambito (RF-CAL-006, T70-02/04).
 *
 * <p>Validaciones, en orden:
 * <ol>
 *   <li>El calendario existe y es del tenant (si no, 404: no se filtra la
 *       existencia de un calendario ajeno).</li>
 *   <li>No esta archivado: asignar un calendario archivado crearia una
 *       asignacion que nunca ganaria la resolucion.</li>
 *   <li>Si el ambito es EMPLOYEE, el destinatario existe en el tenant (si no,
 *       404) y tiene el rol de empleado (si no, 409): un calendario asignado a
 *       quien no ficha nunca llegaria a aplicarse.</li>
 *   <li>No hay ya una asignacion para ese ambito y destinatario. La unicidad se
 *       comprueba aqui <b>y</b> la respalda un indice unico en la V16; la
 *       carrera entre dos peticiones concurrentes la traduce
 *       {@code CalendarAssignmentConstraintTranslator} al mismo codigo de
 *       negocio.</li>
 * </ol>
 */
@Service
public class AssignCalendarUseCase {

    private final CalendarAssignmentRepository assignmentRepository;
    private final WorkCalendarRepository calendarRepository;
    private final EmployeeAssignmentTargetQuery employeeAssignmentTargetQuery;
    private final TenantContext tenantContext;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditRecorder auditRecorder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public AssignCalendarUseCase(
            CalendarAssignmentRepository assignmentRepository,
            WorkCalendarRepository calendarRepository,
            EmployeeAssignmentTargetQuery employeeAssignmentTargetQuery,
            TenantContext tenantContext,
            DomainEventPublisher domainEventPublisher,
            AuditRecorder auditRecorder,
            Clock clock,
            IdGenerator idGenerator) {
        this.assignmentRepository = assignmentRepository;
        this.calendarRepository = calendarRepository;
        this.employeeAssignmentTargetQuery = employeeAssignmentTargetQuery;
        this.tenantContext = tenantContext;
        this.domainEventPublisher = domainEventPublisher;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public CalendarAssignment assign(AssignCalendarCommand command) {
        UUID tenantId = tenantContext.currentTenantId();
        WorkCalendar calendar = calendarRepository
                .findById(tenantId, command.calendarId())
                .orElseThrow(() -> new ResourceNotFoundException("Calendario no encontrado"));
        if (calendar.status() == CalendarStatus.ARCHIVED) {
            throw new CalendarArchivedException();
        }
        // Solo el ambito EMPLOYEE apunta a una persona: TENANT no tiene
        // destinatario y TEAM apunta a un equipo, que este sistema aun no
        // gestiona (ADR-0017).
        // Solo si hay destinatario: que un ambito EMPLOYEE llegue sin el es una
        // incoherencia de la peticion, y de eso responde el agregado con un 400
        // al construir la asignacion. Adelantarse aqui lo convertiria en un 404.
        if (command.scope() == AssignmentScope.EMPLOYEE && command.targetId() != null) {
            switch (employeeAssignmentTargetQuery.check(tenantId, command.targetId())) {
                case UNKNOWN -> throw new ResourceNotFoundException("Empleado no encontrado");
                case NOT_EMPLOYEE -> throw new TargetNotEmployeeException();
                case ASSIGNABLE -> { }
            }
        }
        if (assignmentRepository
                .findByScope(tenantId, command.scope(), command.targetId())
                .isPresent()) {
            throw new CalendarAssignmentAlreadyExistsException(command.scope());
        }

        CalendarAssignment assignment = CalendarAssignment.create(
                tenantId, command.calendarId(), command.scope(), command.targetId(), clock, idGenerator);
        CalendarAssignment saved = assignmentRepository.save(assignment);
        domainEventPublisher.publish(assignment.pullDomainEvents());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("calendarId", saved.calendarId().toString());
        metadata.put("scope", saved.scope().name());
        if (saved.targetId() != null) {
            metadata.put("targetId", saved.targetId().toString());
        }
        auditRecorder.record("CALENDAR_ASSIGNED", "CalendarAssignment", saved.id(), metadata);
        return saved;
    }
}
