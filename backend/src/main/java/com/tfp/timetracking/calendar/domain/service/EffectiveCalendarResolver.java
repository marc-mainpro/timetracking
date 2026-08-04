package com.tfp.timetracking.calendar.domain.service;

import com.tfp.timetracking.calendar.domain.model.CalendarAssignment;
import com.tfp.timetracking.calendar.domain.model.EffectiveCalendar;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Servicio de dominio que implementa la <b>regla de precedencia por ambito</b>
 * de T70-02 / RF-CAL-006: <i>"la asignacion mas especifica prevalece"</i>
 * (empleado &gt; equipo &gt; tenant).
 *
 * <p><b>Esto es un contrato para otros modulos.</b> Turnos (T90, agente B3) y
 * ausencias (T80, agente C1) necesitan saber que calendario rige para un
 * empleado en una fecha. Deben invocar este servicio —directamente o a traves de
 * {@code ResolveEffectiveCalendarUseCase}— y <b>no</b> reimplementar la
 * precedencia: si la regla cambia (p.ej. se intercala un ambito de centro de
 * trabajo), debe cambiar en un unico sitio.
 *
 * <p><b>Algoritmo</b>, en este orden:
 * <ol>
 *   <li><b>Aplicabilidad</b>: se descartan las asignaciones que no apuntan al
 *       empleado ni a su equipo ni al tenant
 *       ({@link CalendarAssignment#appliesTo(UUID, UUID)}).</li>
 *   <li><b>Disponibilidad</b>: se descartan las que referencian un calendario
 *       desconocido, archivado o fuera de vigencia en esa fecha
 *       ({@link WorkCalendar#isApplicableOn(LocalDate)}). Un calendario
 *       archivado o caducado <b>no bloquea</b> el ambito: se cae al siguiente
 *       ambito menos especifico que si tenga calendario disponible.</li>
 *   <li><b>Precedencia</b>: gana la de mayor
 *       {@link com.tfp.timetracking.calendar.domain.model.AssignmentScope#specificity()}.</li>
 *   <li><b>Desempate</b>: la unicidad por {@code (tenant, ambito, destinatario)}
 *       hace que no deba haber empates. Si aun asi se produjera (datos
 *       heredados, carrera), se desempata por el id de la asignacion en orden
 *       ascendente, de modo que la resolucion sea <b>determinista</b> y no
 *       dependa del orden que devuelva la base de datos.</li>
 * </ol>
 *
 * <p><b>El equipo lo aporta el llamante.</b> El sistema no tiene gestion de
 * equipos (ADR-0013): {@code teamId} es un identificador opaco que este modulo
 * no valida ni resuelve. Un {@code teamId} nulo significa "empleado sin equipo
 * conocido" y descarta las asignaciones de ambito {@code TEAM}.
 *
 * <p><b>Fechas locales, no instantes</b> (RNF-011/RNF-012): la resolucion ocurre
 * sobre una {@link LocalDate}. Quien parta de un instante debe convertirlo antes
 * a la fecha local de la zona del tenant.
 *
 * <p>Sin estado y sin dependencias de Spring: es dominio puro invocable desde
 * cualquier capa y trivial de testear.
 */
public final class EffectiveCalendarResolver {

    /**
     * Orden de precedencia: primero el ambito mas especifico; a igual ambito, el
     * id menor en orden <b>lexicografico</b> de su representacion textual.
     *
     * <p>El desempate compara {@code id.toString()} y no los {@link UUID}
     * directamente a proposito: {@link UUID#compareTo(UUID)} compara los bits
     * como {@code long} <i>con signo</i>, asi que un id que empieza por
     * {@code ffffffff} se ordena <i>antes</i> que uno que empieza por
     * {@code 00000000}. Ese orden no coincide con el que ve un humano ni con el
     * que devuelve {@code ORDER BY id} en PostgreSQL, y convertiria el desempate
     * en una sorpresa a la hora de depurar. Es un desempate defensivo que no
     * deberia dispararse nunca —la unicidad por (tenant, ambito, destinatario) lo
     * impide—, pero si se dispara debe ser explicable.
     */
    private static final Comparator<CalendarAssignment> BY_PRECEDENCE = Comparator.comparingInt(
                    (CalendarAssignment assignment) -> assignment.scope().specificity())
            .reversed()
            .thenComparing(assignment -> assignment.id().toString());

    private EffectiveCalendarResolver() {}

    /**
     * Asignacion mas especifica aplicable al empleado, <b>sin</b> mirar la
     * disponibilidad del calendario referenciado.
     *
     * <p>Util para diagnostico y para la administracion ("¿que asignacion rige a
     * este empleado?"). Para calcular jornadas usa
     * {@link #resolve(Collection, Collection, UUID, UUID, LocalDate)}, que ademas
     * comprueba estado y vigencia.
     */
    public static Optional<CalendarAssignment> mostSpecific(
            Collection<CalendarAssignment> assignments, UUID employeeId, UUID teamId) {
        return nullToEmpty(assignments).stream()
                .filter(assignment -> assignment.appliesTo(employeeId, teamId))
                .min(BY_PRECEDENCE);
    }

    /**
     * Resuelve el calendario efectivo de un empleado en una fecha local.
     *
     * @param assignments asignaciones del tenant candidatas (todas las de ambito
     *     {@code TENANT} mas las que apunten a ese empleado o a su equipo)
     * @param calendars calendarios referenciados por esas asignaciones
     * @param employeeId empleado para el que se resuelve
     * @param teamId equipo del empleado, aportado por el llamante; {@code null}
     *     si no pertenece a ninguno
     * @param date fecha local a evaluar
     * @return el calendario efectivo con el dia ya evaluado, o
     *     {@link Optional#empty()} si ningun calendario disponible cubre esa
     *     fecha (situacion legitima: es responsabilidad del llamante decidir el
     *     comportamiento por defecto)
     */
    public static Optional<EffectiveCalendar> resolve(
            Collection<CalendarAssignment> assignments,
            Collection<WorkCalendar> calendars,
            UUID employeeId,
            UUID teamId,
            LocalDate date) {
        Objects.requireNonNull(date, "date no puede ser null");
        Map<UUID, WorkCalendar> calendarsById = nullToEmpty(calendars).stream()
                .collect(java.util.stream.Collectors.toMap(WorkCalendar::id, Function.identity(), (a, b) -> a));

        return nullToEmpty(assignments).stream()
                .filter(assignment -> assignment.appliesTo(employeeId, teamId))
                .filter(assignment -> isAvailable(calendarsById.get(assignment.calendarId()), date))
                .min(BY_PRECEDENCE)
                .map(assignment -> {
                    WorkCalendar calendar = calendarsById.get(assignment.calendarId());
                    return new EffectiveCalendar(assignment, calendar, calendar.dayOf(date));
                });
    }

    private static boolean isAvailable(WorkCalendar calendar, LocalDate date) {
        return calendar != null && calendar.isApplicableOn(date);
    }

    private static <T> Collection<T> nullToEmpty(Collection<T> values) {
        return values == null ? List.of() : values;
    }
}
