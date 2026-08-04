package com.tfp.timetracking.calendar.domain.model;

import com.tfp.timetracking.calendar.domain.event.WorkCalendarArchived;
import com.tfp.timetracking.calendar.domain.event.WorkCalendarCreated;
import com.tfp.timetracking.calendar.domain.event.WorkCalendarUpdated;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Agregado raiz <b>WorkCalendar</b> (T70-01, diseno §5.4 y §9.3, RF-CAL-001..007).
 * Modelo de dominio puro: sin Spring ni JPA (lo verifica {@code DomainPurityTest}).
 *
 * <p><b>Responsabilidades</b> (diseno §9.3):
 * <ul>
 *   <li>Determinar si un dia es laborable ({@link #isWorkingDay(LocalDate)}).</li>
 *   <li>Obtener las horas esperadas de un dia ({@link #expectedHours(LocalDate)}).</li>
 *   <li>Aplicar jornadas especiales ({@link #dayOf(LocalDate)}).</li>
 * </ul>
 *
 * <p><b>Tiempo local, no instantes</b> (RNF-011/RNF-012). Un calendario opera
 * sobre <i>fechas locales</i>: "el 6 de enero es festivo" no es un instante, es
 * un dia del calendario civil. Por eso la vigencia, los festivos y las jornadas
 * especiales usan {@link LocalDate}, y solo {@code createdAt}/{@code updatedAt}
 * —marcas de auditoria tecnica— son {@link Instant} en UTC. La conversion a la
 * linea temporal se hace <b>en los bordes</b> con {@link #startOfDay(LocalDate)}
 * y {@link #endOfDayExclusive(LocalDate)}, que respetan los saltos de horario de
 * verano: en {@code Europe/Madrid} el dia del cambio de primavera dura 23 h y el
 * de otono 25 h, aunque su jornada esperada siga siendo la misma.
 *
 * <p><b>Precedencia dentro del calendario</b> ({@link DaySource}): fuera de
 * vigencia &gt; jornada especial &gt; festivo &gt; regla semanal. Los dias de la
 * semana sin regla explicita se consideran no laborables.
 */
public final class WorkCalendar {

    /** Longitud maxima del nombre, alineada con la columna de la migracion V16. */
    public static final int MAX_NAME_LENGTH = 120;

    private final UUID id;
    private final UUID tenantId;
    private String name;
    private String timezone;
    private LocalDate validFrom;
    private LocalDate validTo;
    private CalendarStatus status;
    private final Map<DayOfWeek, CalendarDayRule> dayRules = new EnumMap<>(DayOfWeek.class);
    private final Map<LocalDate, Holiday> holidays = new java.util.TreeMap<>();
    private final Map<LocalDate, SpecialDay> specialDays = new java.util.TreeMap<>();
    private final long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private final List<Object> domainEvents = new ArrayList<>();

    private WorkCalendar(
            UUID id,
            UUID tenantId,
            String name,
            String timezone,
            LocalDate validFrom,
            LocalDate validTo,
            CalendarStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.timezone = timezone;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factoria: crea un calendario laboral {@code ACTIVE} y emite
     * {@link WorkCalendarCreated} (RF-CAL-001).
     *
     * @param timezone zona horaria IANA del calendario (RF-CAL-007); normalmente
     *     la del tenant, pero se guarda en el propio calendario para que una
     *     organizacion multi-sede pueda tener calendarios en husos distintos
     */
    public static WorkCalendar create(
            UUID tenantId,
            String name,
            String timezone,
            LocalDate validFrom,
            LocalDate validTo,
            Collection<CalendarDayRule> dayRules,
            Collection<Holiday> holidays,
            Collection<SpecialDay> specialDays,
            Clock clock,
            IdGenerator idGenerator) {
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");

        String validatedName = validateName(name);
        String validatedTimezone = validateTimezone(timezone);
        validatePeriod(validFrom, validTo);

        UUID calendarId = idGenerator.newId();
        Instant now = clock.now();
        WorkCalendar calendar = new WorkCalendar(
                calendarId,
                tenantId,
                validatedName,
                validatedTimezone,
                validFrom,
                validTo,
                CalendarStatus.ACTIVE,
                0L,
                now,
                now);
        calendar.replaceRules(dayRules, holidays, specialDays);
        calendar.domainEvents.add(new WorkCalendarCreated(
                idGenerator.newId(), now, tenantId, calendarId, validatedName, validatedTimezone, validFrom, validTo));
        return calendar;
    }

    /** Reconstruye un calendario desde persistencia. No genera eventos de dominio. */
    public static WorkCalendar reconstitute(
            UUID id,
            UUID tenantId,
            String name,
            String timezone,
            LocalDate validFrom,
            LocalDate validTo,
            CalendarStatus status,
            Collection<CalendarDayRule> dayRules,
            Collection<Holiday> holidays,
            Collection<SpecialDay> specialDays,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(status, "status no puede ser null");
        Objects.requireNonNull(createdAt, "createdAt no puede ser null");
        Objects.requireNonNull(updatedAt, "updatedAt no puede ser null");
        validatePeriod(validFrom, validTo);
        WorkCalendar calendar = new WorkCalendar(
                id,
                tenantId,
                validateName(name),
                validateTimezone(timezone),
                validFrom,
                validTo,
                status,
                version,
                createdAt,
                updatedAt);
        calendar.replaceRules(dayRules, holidays, specialDays);
        return calendar;
    }

    /**
     * Edita el calendario por completo (RF-CAL-001..005, T70-04). Las
     * colecciones se sustituyen enteras: el cliente envia el estado deseado, no
     * un delta. Emite {@link WorkCalendarUpdated}.
     *
     * @throws CalendarArchivedException si el calendario esta archivado
     */
    public void update(
            String name,
            String timezone,
            LocalDate validFrom,
            LocalDate validTo,
            Collection<CalendarDayRule> dayRules,
            Collection<Holiday> holidays,
            Collection<SpecialDay> specialDays,
            Clock clock,
            IdGenerator idGenerator) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        requireActive();

        String validatedName = validateName(name);
        String validatedTimezone = validateTimezone(timezone);
        validatePeriod(validFrom, validTo);

        this.name = validatedName;
        this.timezone = validatedTimezone;
        this.validFrom = validFrom;
        this.validTo = validTo;
        replaceRules(dayRules, holidays, specialDays);

        Instant now = clock.now();
        this.updatedAt = now;
        this.domainEvents.add(new WorkCalendarUpdated(
                idGenerator.newId(), now, tenantId, id, validatedName, validatedTimezone, validFrom, validTo));
    }

    /**
     * Archiva el calendario (T70-04). Un calendario archivado ya no participa en
     * la resolucion del calendario efectivo. Emite {@link WorkCalendarArchived}.
     *
     * @throws CalendarArchivedException si ya estaba archivado
     */
    public void archive(Clock clock, IdGenerator idGenerator) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        requireActive();
        Instant now = clock.now();
        this.status = CalendarStatus.ARCHIVED;
        this.updatedAt = now;
        this.domainEvents.add(new WorkCalendarArchived(idGenerator.newId(), now, tenantId, id, name));
    }

    // ---------------------------------------------------------------------
    // Responsabilidades de negocio (diseno §9.3)
    // ---------------------------------------------------------------------

    /**
     * Vigencia temporal (RF-CAL-005): {@code true} si la fecha local esta dentro
     * de {@code [validFrom, validTo]}. {@code validTo == null} significa vigencia
     * indefinida. Ambos extremos son inclusivos.
     */
    public boolean isEffectiveOn(LocalDate date) {
        Objects.requireNonNull(date, "date no puede ser null");
        if (date.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || !date.isAfter(validTo);
    }

    /** {@code true} si el calendario esta activo y vigente en esa fecha local. */
    public boolean isApplicableOn(LocalDate date) {
        return status == CalendarStatus.ACTIVE && isEffectiveOn(date);
    }

    /** Determina si un dia local es laborable (diseno §9.3). */
    public boolean isWorkingDay(LocalDate date) {
        return dayOf(date).working();
    }

    /** Horas esperadas de un dia local (diseno §9.3); {@link Duration#ZERO} si no es laborable. */
    public Duration expectedHours(LocalDate date) {
        return dayOf(date).expectedDuration();
    }

    /**
     * Evalua el calendario sobre una fecha local aplicando la precedencia
     * completa: fuera de vigencia &gt; jornada especial &gt; festivo &gt; regla
     * semanal (diseno §9.3, "aplicar jornadas especiales").
     */
    public CalendarDay dayOf(LocalDate date) {
        Objects.requireNonNull(date, "date no puede ser null");
        if (!isEffectiveOn(date)) {
            return new CalendarDay(date, false, 0, DaySource.OUT_OF_VALIDITY);
        }
        SpecialDay specialDay = specialDays.get(date);
        if (specialDay != null) {
            return new CalendarDay(date, specialDay.working(), specialDay.expectedMinutes(), DaySource.SPECIAL_DAY);
        }
        if (holidays.containsKey(date)) {
            return new CalendarDay(date, false, 0, DaySource.HOLIDAY);
        }
        CalendarDayRule rule = dayRules.get(date.getDayOfWeek());
        if (rule == null || !rule.working()) {
            return new CalendarDay(date, false, 0, DaySource.WEEKLY_RULE);
        }
        return new CalendarDay(date, true, rule.expectedMinutes(), DaySource.WEEKLY_RULE);
    }

    // ---------------------------------------------------------------------
    // Conversion en los bordes (RNF-011: los instantes se almacenan en UTC)
    // ---------------------------------------------------------------------

    /** Zona horaria IANA del calendario (RF-CAL-007). */
    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    /**
     * Instante UTC en el que empieza esa fecha local en la zona del calendario.
     * Es el <b>unico</b> punto donde una fecha del calendario se convierte en un
     * punto de la linea temporal (RNF-011).
     *
     * <p>En un cambio de horario de verano el resultado sigue siendo correcto:
     * {@link java.time.ZonedDateTime} resuelve el hueco (primavera) adelantando
     * al primer instante valido del dia.
     */
    public Instant startOfDay(LocalDate date) {
        Objects.requireNonNull(date, "date no puede ser null");
        return date.atStartOfDay(zoneId()).toInstant();
    }

    /**
     * Instante UTC en el que empieza el dia local siguiente, es decir, el limite
     * superior <b>exclusivo</b> de esa fecha local.
     *
     * <p>{@code Duration.between(startOfDay(d), endOfDayExclusive(d))} devuelve
     * la duracion real del dia civil: 23 h el dia del adelanto de hora y 25 h el
     * del atraso en {@code Europe/Madrid}.
     */
    public Instant endOfDayExclusive(LocalDate date) {
        Objects.requireNonNull(date, "date no puede ser null");
        return date.plusDays(1).atStartOfDay(zoneId()).toInstant();
    }

    /** Duracion real del dia civil en la zona del calendario (23 h, 24 h o 25 h). */
    public Duration civilDayLength(LocalDate date) {
        return Duration.between(startOfDay(date), endOfDayExclusive(date));
    }

    /** Devuelve y limpia los eventos de dominio acumulados por el agregado. */
    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    // ---------------------------------------------------------------------
    // Invariantes
    // ---------------------------------------------------------------------

    private void replaceRules(
            Collection<CalendarDayRule> newDayRules,
            Collection<Holiday> newHolidays,
            Collection<SpecialDay> newSpecialDays) {
        Map<DayOfWeek, CalendarDayRule> rules = new EnumMap<>(DayOfWeek.class);
        for (CalendarDayRule rule : nullToEmpty(newDayRules)) {
            Objects.requireNonNull(rule, "Una regla semanal no puede ser null");
            if (rules.putIfAbsent(rule.dayOfWeek(), rule) != null) {
                throw new DuplicateDayRuleException(rule.dayOfWeek());
            }
        }

        Set<LocalDate> usedDates = new HashSet<>();
        Map<LocalDate, Holiday> holidaysByDate = new java.util.TreeMap<>();
        for (Holiday holiday : nullToEmpty(newHolidays)) {
            Objects.requireNonNull(holiday, "Un festivo no puede ser null");
            if (!usedDates.add(holiday.date())) {
                throw new DuplicateCalendarDateException(holiday.date());
            }
            holidaysByDate.put(holiday.date(), holiday);
        }
        Map<LocalDate, SpecialDay> specialDaysByDate = new java.util.TreeMap<>();
        for (SpecialDay specialDay : nullToEmpty(newSpecialDays)) {
            Objects.requireNonNull(specialDay, "Una jornada especial no puede ser null");
            if (!usedDates.add(specialDay.date())) {
                throw new DuplicateCalendarDateException(specialDay.date());
            }
            specialDaysByDate.put(specialDay.date(), specialDay);
        }

        this.dayRules.clear();
        this.dayRules.putAll(rules);
        this.holidays.clear();
        this.holidays.putAll(holidaysByDate);
        this.specialDays.clear();
        this.specialDays.putAll(specialDaysByDate);
    }

    private void requireActive() {
        if (status != CalendarStatus.ACTIVE) {
            throw new CalendarArchivedException();
        }
    }

    private static <T> Collection<T> nullToEmpty(Collection<T> values) {
        return values == null ? List.of() : values;
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del calendario es obligatorio");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "El nombre del calendario no puede superar " + MAX_NAME_LENGTH + " caracteres");
        }
        return trimmed;
    }

    private static String validateTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("La zona horaria del calendario es obligatoria");
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Zona horaria IANA invalida: " + timezone, e);
        }
        return timezone;
    }

    private static void validatePeriod(LocalDate validFrom, LocalDate validTo) {
        if (validFrom == null) {
            throw new IllegalArgumentException("La fecha de inicio de vigencia es obligatoria");
        }
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("La vigencia no puede terminar antes de empezar");
        }
    }

    // ---------------------------------------------------------------------
    // Accesores (estilo record)
    // ---------------------------------------------------------------------

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public String timezone() {
        return timezone;
    }

    public LocalDate validFrom() {
        return validFrom;
    }

    public LocalDate validTo() {
        return validTo;
    }

    public CalendarStatus status() {
        return status;
    }

    public List<CalendarDayRule> dayRules() {
        return List.copyOf(dayRules.values());
    }

    public List<Holiday> holidays() {
        return List.copyOf(holidays.values());
    }

    public List<SpecialDay> specialDays() {
        return List.copyOf(specialDays.values());
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
