package com.tfp.timetracking.calendar.application.command;

import java.time.DayOfWeek;

/**
 * Regla semanal enviada por el cliente (RF-CAL-002). Se traduce a
 * {@code CalendarDayRule} en el caso de uso; el dominio valida la coherencia
 * entre {@code working} y {@code expectedMinutes}.
 */
public record DayRuleCommand(DayOfWeek dayOfWeek, boolean working, int expectedMinutes) {}
