package com.tfp.timetracking.calendar.application.usecase;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.calendar.application.command.SaveWorkCalendarCommand;
import com.tfp.timetracking.calendar.domain.model.DuplicateCalendarNameException;
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
 * Edita un calendario laboral (RF-CAL-001..005, T70-04). Sustituye por completo
 * reglas semanales, festivos y jornadas especiales.
 *
 * <p>Si el calendario no existe <b>o es de otro tenant</b> responde 404 y no 403
 * (ADR-0002): un 403 confirmaria la existencia del recurso ajeno.
 */
@Service
public class UpdateWorkCalendarUseCase {

    private final WorkCalendarRepository calendarRepository;
    private final CalendarCommandTranslator translator;
    private final TenantContext tenantContext;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditRecorder auditRecorder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public UpdateWorkCalendarUseCase(
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
    public WorkCalendar update(UUID calendarId, SaveWorkCalendarCommand command) {
        var tenantId = tenantContext.currentTenantId();
        WorkCalendar calendar = calendarRepository
                .findById(tenantId, calendarId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendario no encontrado"));

        // Solo se comprueba el duplicado si el nombre cambia: si no, el propio
        // calendario haria que existsByName devolviera true.
        String newName = command.name() == null ? null : command.name().trim();
        if (newName != null && !newName.equals(calendar.name()) && calendarRepository.existsByName(tenantId, newName)) {
            throw new DuplicateCalendarNameException();
        }

        calendar.update(
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
                "CALENDAR_UPDATED",
                "WorkCalendar",
                saved.id(),
                Map.of(
                        "name", saved.name(),
                        "timezone", saved.timezone(),
                        "holidays", saved.holidays().size(),
                        "specialDays", saved.specialDays().size()));
        return saved;
    }
}
