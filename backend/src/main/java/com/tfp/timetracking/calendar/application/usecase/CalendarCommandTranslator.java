package com.tfp.timetracking.calendar.application.usecase;

import com.tfp.timetracking.calendar.application.CalendarProperties;
import com.tfp.timetracking.calendar.application.command.SaveWorkCalendarCommand;
import com.tfp.timetracking.calendar.domain.model.CalendarDayRule;
import com.tfp.timetracking.calendar.domain.model.Holiday;
import com.tfp.timetracking.calendar.domain.model.SpecialDay;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Traduce un {@link SaveWorkCalendarCommand} a los objetos de valor del dominio
 * y aplica las cotas defensivas de {@link CalendarProperties}.
 *
 * <p>Existe para no duplicar esa traduccion entre el caso de uso de creacion y
 * el de edicion, que reciben exactamente el mismo comando.
 */
@Component
public class CalendarCommandTranslator {

    private final CalendarProperties properties;

    public CalendarCommandTranslator(CalendarProperties properties) {
        this.properties = properties;
    }

    /** Zona horaria del comando o, si viene vacia, la configurada por defecto. */
    public String timezoneOrDefault(SaveWorkCalendarCommand command) {
        String timezone = command.timezone();
        return timezone == null || timezone.isBlank() ? properties.defaultTimezone() : timezone.trim();
    }

    public List<CalendarDayRule> dayRules(SaveWorkCalendarCommand command) {
        return command.dayRules().stream()
                .map(rule -> CalendarDayRule.of(rule.dayOfWeek(), rule.working(), rule.expectedMinutes()))
                .toList();
    }

    /** @throws IllegalArgumentException si se supera la cota configurada */
    public List<Holiday> holidays(SaveWorkCalendarCommand command) {
        if (command.holidays().size() > properties.maxHolidaysPerCalendar()) {
            throw new IllegalArgumentException(
                    "Un calendario no puede tener mas de " + properties.maxHolidaysPerCalendar() + " festivos");
        }
        return command.holidays().stream()
                .map(holiday -> new Holiday(holiday.date(), holiday.name()))
                .toList();
    }

    /** @throws IllegalArgumentException si se supera la cota configurada */
    public List<SpecialDay> specialDays(SaveWorkCalendarCommand command) {
        if (command.specialDays().size() > properties.maxSpecialDaysPerCalendar()) {
            throw new IllegalArgumentException("Un calendario no puede tener mas de "
                    + properties.maxSpecialDaysPerCalendar() + " jornadas especiales");
        }
        return command.specialDays().stream()
                .map(day -> new SpecialDay(day.date(), day.name(), day.expectedMinutes()))
                .toList();
    }
}
