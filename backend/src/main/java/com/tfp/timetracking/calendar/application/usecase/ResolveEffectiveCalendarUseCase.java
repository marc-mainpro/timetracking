package com.tfp.timetracking.calendar.application.usecase;

import com.tfp.timetracking.calendar.application.dto.EffectiveCalendarView;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignment;
import com.tfp.timetracking.calendar.domain.model.EffectiveCalendar;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import com.tfp.timetracking.calendar.domain.repository.CalendarAssignmentRepository;
import com.tfp.timetracking.calendar.domain.repository.WorkCalendarRepository;
import com.tfp.timetracking.calendar.domain.service.EffectiveCalendarResolver;
import com.tfp.timetracking.shared.application.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve el <b>calendario efectivo</b> de un empleado en una fecha local
 * (T70-04) aplicando la precedencia por ambito de
 * {@link EffectiveCalendarResolver} (empleado &gt; equipo &gt; tenant).
 *
 * <p><b>Punto de entrada para otros modulos.</b> Turnos (T90) y ausencias (T80)
 * deben inyectar este caso de uso —o el resolver de dominio si ya tienen los
 * agregados cargados— en lugar de consultar las tablas de calendario o
 * reimplementar la precedencia. La firma es deliberadamente estable:
 * {@code (employeeId, teamId, date) -> Optional<EffectiveCalendar>}.
 *
 * <p><b>El equipo lo aporta el llamante.</b> El sistema no tiene gestion de
 * equipos (ADR-0013), asi que este modulo no sabe a que equipo pertenece un
 * empleado: quien invoca pasa el {@code teamId} o {@code null}. Cuando exista un
 * modulo de equipos, bastara con resolverlo aqui sin cambiar el contrato del
 * dominio.
 *
 * <p>{@link Optional#empty()} es una respuesta legitima: significa que ningun
 * calendario disponible cubre esa fecha para ese empleado. Decidir que hacer
 * entonces (rechazar, asumir jornada estandar) es del llamante, no del
 * calendario.
 */
@Service
public class ResolveEffectiveCalendarUseCase {

    private final CalendarAssignmentRepository assignmentRepository;
    private final WorkCalendarRepository calendarRepository;
    private final TenantContext tenantContext;

    public ResolveEffectiveCalendarUseCase(
            CalendarAssignmentRepository assignmentRepository,
            WorkCalendarRepository calendarRepository,
            TenantContext tenantContext) {
        this.assignmentRepository = assignmentRepository;
        this.calendarRepository = calendarRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public Optional<EffectiveCalendar> resolve(UUID employeeId, UUID teamId, LocalDate date) {
        Objects.requireNonNull(employeeId, "employeeId no puede ser null");
        Objects.requireNonNull(date, "date no puede ser null");
        UUID tenantId = tenantContext.currentTenantId();

        List<CalendarAssignment> assignments = assignmentRepository.findApplicable(tenantId, employeeId, teamId);
        if (assignments.isEmpty()) {
            return Optional.empty();
        }
        List<UUID> calendarIds =
                assignments.stream().map(CalendarAssignment::calendarId).distinct().toList();
        List<WorkCalendar> calendars = calendarRepository.findAllByIds(tenantId, calendarIds);

        return EffectiveCalendarResolver.resolve(assignments, calendars, employeeId, teamId, date);
    }

    /**
     * Misma resolucion que {@link #resolve(UUID, UUID, LocalDate)} pero devuelta
     * como proyeccion plana. Es la que consume el borde REST: asi el controlador
     * no manipula agregados de dominio y no hace falta anadir una excepcion a
     * {@code LayeredArchitectureTest} (ADR-0011).
     */
    @Transactional(readOnly = true)
    public Optional<EffectiveCalendarView> resolveView(UUID employeeId, UUID teamId, LocalDate date) {
        return resolve(employeeId, teamId, date).map(EffectiveCalendarView::from);
    }
}
