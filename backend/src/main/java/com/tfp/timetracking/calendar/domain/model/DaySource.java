package com.tfp.timetracking.calendar.domain.model;

/**
 * Origen de la respuesta de {@link WorkCalendar#dayOf(java.time.LocalDate)}: que
 * regla del calendario ha decidido si el dia es laborable y cuantas horas se
 * esperan. Se expone al consumidor (API, turnos, ausencias) para que pueda
 * explicar el resultado sin reimplementar la precedencia.
 *
 * <p>Precedencia dentro de un calendario, de mayor a menor:
 * {@code OUT_OF_VALIDITY} &gt; {@code SPECIAL_DAY} &gt; {@code HOLIDAY} &gt;
 * {@code WEEKLY_RULE}.
 */
public enum DaySource {

    /** La fecha cae fuera del periodo de vigencia del calendario (RF-CAL-005). */
    OUT_OF_VALIDITY,

    /** Ha decidido una jornada especial para esa fecha concreta (RF-CAL-004). */
    SPECIAL_DAY,

    /** Ha decidido un festivo registrado en esa fecha (RF-CAL-003). */
    HOLIDAY,

    /** Ha decidido la regla semanal del dia de la semana (RF-CAL-002). */
    WEEKLY_RULE
}
