package com.tfp.timetracking.calendar.application.usecase;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.calendar.application.command.SaveWorkCalendarCommand;
import com.tfp.timetracking.calendar.domain.model.DuplicateCalendarNameException;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import com.tfp.timetracking.calendar.domain.repository.WorkCalendarRepository;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea un calendario laboral (RF-CAL-001, T70-04). El tenant se resuelve del
 * principal autenticado, nunca del cuerpo de la peticion.
 */
@Service
public class CreateWorkCalendarUseCase {

    private final WorkCalendarRepository calendarRepository;
    private final CalendarCommandTranslator translator;
    private final TenantContext tenantContext;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditRecorder auditRecorder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public CreateWorkCalendarUseCase(
            WorkCalendarRepository calendarRepository,
            CalendarCommandTranslator translator,
            TenantContext tenantContext,
            DomainEventPublisher domainEventPublisher,
            AuditRecorder auditRecorder,
            Clock clock,
            IdGenerator idGenerator) {
        this.calendarRepository = calendarRepository;
        this.translator = translator;
        this.tenantContext = tenantContext;
        this.domainEventPublisher = domainEventPublisher;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public WorkCalendar create(SaveWorkCalendarCommand command) {
        var tenantId = tenantContext.currentTenantId();
        // Chequeo previo para dar el error de negocio correcto en el caso comun;
        // la carrera concurrente la cubre ux_work_calendar_tenant_name (V16) via
        // DuplicateCalendarNameConstraintTranslator.
        if (command.name() != null && calendarRepository.existsByName(tenantId, command.name().trim())) {
            throw new DuplicateCalendarNameException();
        }
        WorkCalendar calendar = WorkCalendar.create(
                tenantId,
                command.name(),
                translator.timezoneOrDefault(command),
                command.validFrom(),
                command.validTo(),
                translator.dayRules(command),
                translator.holidays(command),
                translator.specialDays(command),
                clock,
                idGenerator);

        WorkCalendar saved = calendarRepository.save(calendar);
        domainEventPublisher.publish(calendar.pullDomainEvents());
        auditRecorder.record(
                "CALENDAR_CREATED",
                "WorkCalendar",
                saved.id(),
                Map.of("name", saved.name(), "timezone", saved.timezone()));
        return saved;
    }
}
