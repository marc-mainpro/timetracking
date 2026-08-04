package com.tfp.timetracking.calendar.interfaces.rest;

import com.tfp.timetracking.calendar.application.command.AssignCalendarCommand;
import com.tfp.timetracking.calendar.application.command.DayRuleCommand;
import com.tfp.timetracking.calendar.application.command.HolidayCommand;
import com.tfp.timetracking.calendar.application.command.SaveWorkCalendarCommand;
import com.tfp.timetracking.calendar.application.command.SpecialDayCommand;
import com.tfp.timetracking.calendar.application.dto.EffectiveCalendarView;
import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignment;
import com.tfp.timetracking.calendar.domain.model.CalendarStatus;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Traduce entre los DTO del borde REST y los comandos/agregados de las capas
 * internas. Es traduccion, no logica de negocio: es la excepcion por convencion
 * que {@code LayeredArchitectureTest} concede a las clases {@code *RestMapper}
 * (ADR-0011).
 */
@Component
public class CalendarRestMapper {

    public SaveWorkCalendarCommand toCommand(SaveCalendarRequest request) {
        return new SaveWorkCalendarCommand(
                request.name(),
                request.timezone(),
                request.validFrom(),
                request.validTo(),
                request.dayRules() == null
                        ? List.of()
                        : request.dayRules().stream()
                                .map(rule -> new DayRuleCommand(
                                        rule.dayOfWeek(), rule.working(), rule.expectedMinutes()))
                                .toList(),
                request.holidays() == null
                        ? List.of()
                        : request.holidays().stream()
                                .map(holiday -> new HolidayCommand(holiday.date(), holiday.name()))
                                .toList(),
                request.specialDays() == null
                        ? List.of()
                        : request.specialDays().stream()
                                .map(day -> new SpecialDayCommand(day.date(), day.name(), day.expectedMinutes()))
                                .toList());
    }

    /**
     * @throws IllegalArgumentException si el ambito no es uno de los valores
     *     admitidos; el manejador global lo traduce a 400 (ADR-0006)
     */
    public AssignCalendarCommand toCommand(AssignCalendarRequest request) {
        return new AssignCalendarCommand(request.calendarId(), toScope(request.scope()), request.targetId());
    }

    /** Traduce el ambito recibido como texto, con un mensaje util en vez de un enum error. */
    public AssignmentScope toScope(String scope) {
        if (scope == null) {
            throw new IllegalArgumentException("El ambito de la asignacion es obligatorio");
        }
        try {
            return AssignmentScope.valueOf(scope.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ambito de asignacion invalido: " + scope, e);
        }
    }

    /**
     * Traduce el filtro de estado de la query string; {@code null} o vacio
     * significa "todos".
     *
     * @throws IllegalArgumentException si el estado no es uno de los admitidos
     */
    public CalendarStatus toStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CalendarStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de calendario invalido: " + status, e);
        }
    }

    public CalendarSummaryResponse toSummary(WorkCalendar calendar) {
        return new CalendarSummaryResponse(
                calendar.id(),
                calendar.name(),
                calendar.timezone(),
                calendar.validFrom(),
                calendar.validTo(),
                calendar.status().name(),
                calendar.holidays().size(),
                calendar.specialDays().size());
    }

    public CalendarDetailResponse toDetail(WorkCalendar calendar) {
        return new CalendarDetailResponse(
                calendar.id(),
                calendar.name(),
                calendar.timezone(),
                calendar.validFrom(),
                calendar.validTo(),
                calendar.status().name(),
                calendar.dayRules().stream()
                        .map(rule -> new DayRulePayload(rule.dayOfWeek(), rule.working(), rule.expectedMinutes()))
                        .toList(),
                calendar.holidays().stream()
                        .map(holiday -> new HolidayPayload(holiday.date(), holiday.name()))
                        .toList(),
                calendar.specialDays().stream()
                        .map(day -> new SpecialDayPayload(day.date(), day.name(), day.expectedMinutes()))
                        .toList(),
                calendar.version(),
                calendar.createdAt(),
                calendar.updatedAt());
    }

    public PagedCalendarsResponse toPagedResponse(PagedResult<WorkCalendar> result) {
        return new PagedCalendarsResponse(
                result.content().stream().map(this::toSummary).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    public CalendarAssignmentResponse toResponse(CalendarAssignment assignment) {
        return new CalendarAssignmentResponse(
                assignment.id(),
                assignment.calendarId(),
                assignment.scope().name(),
                assignment.targetId(),
                assignment.createdAt());
    }

    public PagedCalendarAssignmentsResponse toPagedAssignmentsResponse(PagedResult<CalendarAssignment> result) {
        return new PagedCalendarAssignmentsResponse(
                result.content().stream().map(this::toResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    public EffectiveCalendarResponse toResponse(EffectiveCalendarView view) {
        return new EffectiveCalendarResponse(
                view.calendarId(),
                view.calendarName(),
                view.timezone(),
                view.assignmentId(),
                view.scope(),
                view.targetId(),
                view.date(),
                view.working(),
                view.expectedMinutes(),
                view.source().name());
    }
}
