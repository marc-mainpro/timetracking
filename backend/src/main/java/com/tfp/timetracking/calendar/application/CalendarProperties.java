package com.tfp.timetracking.calendar.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del modulo {@code calendar} (fichero
 * {@code config/calendar.yml}, ADR-0011). Sigue el patron de
 * {@code OutboxProperties}: el record vive en {@code application} —lo consumen
 * los casos de uso— y se activa con {@code @EnableConfigurationProperties} desde
 * {@code infrastructure.configuration}.
 *
 * <p>Los limites no son reglas de negocio del dominio sino <b>cotas defensivas
 * de entrada</b>: impiden que un cliente envie un calendario con cien mil
 * festivos y convierta un {@code PUT} en una denegacion de servicio. Por eso se
 * validan en el caso de uso y no en el agregado.
 *
 * @param maxHolidaysPerCalendar maximo de festivos admitidos en un calendario
 * @param maxSpecialDaysPerCalendar maximo de jornadas especiales admitidas
 * @param defaultTimezone zona horaria IANA usada cuando el cliente no envia una
 */
@ConfigurationProperties(prefix = "calendar")
public record CalendarProperties(
        int maxHolidaysPerCalendar, int maxSpecialDaysPerCalendar, String defaultTimezone) {

    public CalendarProperties {
        if (maxHolidaysPerCalendar <= 0) {
            maxHolidaysPerCalendar = 400;
        }
        if (maxSpecialDaysPerCalendar <= 0) {
            maxSpecialDaysPerCalendar = 400;
        }
        if (defaultTimezone == null || defaultTimezone.isBlank()) {
            defaultTimezone = "UTC";
        }
    }
}
