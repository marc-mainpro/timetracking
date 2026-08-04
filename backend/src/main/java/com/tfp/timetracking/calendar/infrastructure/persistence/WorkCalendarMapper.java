package com.tfp.timetracking.calendar.infrastructure.persistence;

import com.tfp.timetracking.calendar.domain.model.CalendarDayRule;
import com.tfp.timetracking.calendar.domain.model.CalendarStatus;
import com.tfp.timetracking.calendar.domain.model.Holiday;
import com.tfp.timetracking.calendar.domain.model.SpecialDay;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import java.time.DayOfWeek;
import java.util.List;

/**
 * Mapper dominio &lt;-&gt; JPA para {@link WorkCalendar} (CONTEXT-GLOBAL §4).
 *
 * <p>Las colecciones hijas se reconstruyen enteras en cada guardado
 * ({@code clear()} + {@code addAll()}) porque el agregado ya las trata como una
 * sustitucion completa: con {@code orphanRemoval = true} Hibernate traduce eso a
 * los DELETE/INSERT necesarios. Emparejar elemento a elemento no aportaria nada
 * —no hay ids artificiales que preservar— y complicaria el mapper.
 */
final class WorkCalendarMapper {

    private WorkCalendarMapper() {}

    static WorkCalendarJpaEntity toJpaEntity(WorkCalendar calendar) {
        WorkCalendarJpaEntity entity = new WorkCalendarJpaEntity(
                calendar.id(),
                calendar.tenantId(),
                calendar.name(),
                calendar.timezone(),
                calendar.validFrom(),
                calendar.validTo(),
                calendar.status().name(),
                calendar.version(),
                calendar.createdAt(),
                calendar.updatedAt());
        copyChildren(calendar, entity);
        return entity;
    }

    /** Vuelca el estado del agregado sobre una entidad ya gestionada por el contexto de persistencia. */
    static void copyChildren(WorkCalendar calendar, WorkCalendarJpaEntity entity) {
        entity.getDayRules().clear();
        for (CalendarDayRule rule : calendar.dayRules()) {
            entity.getDayRules()
                    .add(new CalendarDayRuleJpaEntity(
                            entity, rule.dayOfWeek().name(), rule.working(), rule.expectedMinutes()));
        }
        entity.getHolidays().clear();
        for (Holiday holiday : calendar.holidays()) {
            entity.getHolidays().add(new CalendarHolidayJpaEntity(entity, holiday.date(), holiday.name()));
        }
        entity.getSpecialDays().clear();
        for (SpecialDay specialDay : calendar.specialDays()) {
            entity.getSpecialDays()
                    .add(new CalendarSpecialDayJpaEntity(
                            entity, specialDay.date(), specialDay.name(), specialDay.expectedMinutes()));
        }
    }

    static WorkCalendar toDomain(WorkCalendarJpaEntity entity) {
        List<CalendarDayRule> dayRules = entity.getDayRules().stream()
                .map(rule -> CalendarDayRule.of(
                        DayOfWeek.valueOf(rule.getDayOfWeek()), rule.isWorking(), rule.getExpectedMinutes()))
                .toList();
        List<Holiday> holidays = entity.getHolidays().stream()
                .map(holiday -> new Holiday(holiday.getDate(), holiday.getName()))
                .toList();
        List<SpecialDay> specialDays = entity.getSpecialDays().stream()
                .map(day -> new SpecialDay(day.getDate(), day.getName(), day.getExpectedMinutes()))
                .toList();
        return WorkCalendar.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getTimezone(),
                entity.getValidFrom(),
                entity.getValidTo(),
                CalendarStatus.valueOf(entity.getStatus()),
                dayRules,
                holidays,
                specialDays,
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
