package com.tfp.timetracking.calendar.application.usecase;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import com.tfp.timetracking.calendar.domain.repository.WorkCalendarRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archiva un calendario laboral (T70-04). Es el borrado logico que expone
 * {@code DELETE /api/v1/admin/calendars/{id}}: no se borra fisicamente porque
 * las jornadas ya calculadas y las asignaciones historicas lo referencian.
 *
 * <p>Las asignaciones que apuntan a un calendario archivado se conservan pero
 * dejan de ganar la resolucion; el empleado cae al ambito menos especifico que
 * si tenga un calendario disponible (ver {@code EffectiveCalendarResolver}).
 */
@Service
public class ArchiveWorkCalendarUseCase {

    private final WorkCalendarRepository calendarRepository;
    private final TenantContext tenantContext;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditRecorder auditRecorder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public ArchiveWorkCalendarUseCase(
            WorkCalendarRepository calendarRepository,
            TenantContext tenantContext,
            DomainEventPublisher domainEventPublisher,
            AuditRecorder auditRecorder,
            Clock clock,
            IdGenerator idGenerator) {
        this.calendarRepository = calendarRepository;
        this.tenantContext = tenantContext;
        this.domainEventPublisher = domainEventPublisher;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public WorkCalendar archive(UUID calendarId) {
        var tenantId = tenantContext.currentTenantId();
        WorkCalendar calendar = calendarRepository
                .findById(tenantId, calendarId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendario no encontrado"));

        calendar.archive(clock, idGenerator);
        WorkCalendar saved = calendarRepository.save(calendar);
        domainEventPublisher.publish(calendar.pullDomainEvents());
        auditRecorder.record("CALENDAR_ARCHIVED", "WorkCalendar", saved.id(), Map.of("name", saved.name()));
        return saved;
    }
}
