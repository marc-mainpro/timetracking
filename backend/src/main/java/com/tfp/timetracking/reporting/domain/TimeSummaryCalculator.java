package com.tfp.timetracking.reporting.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Servicio de dominio puro (sin Spring/JPA) que calcula los informes de
 * tiempo trabajado a partir de {@link WorkdayReportEntry} (T801).
 *
 * <p>Reglas de negocio fijadas para T801:
 * <ul>
 *   <li>Trabajado = jornada menos pausas cerradas, igual que
 *       {@code WorkdayRestMapper.workedDuration}.</li>
 *   <li>Las jornadas abiertas ({@code OPEN}/{@code ON_BREAK}) se excluyen del
 *       tiempo trabajado/pausado (no hay forma fiable de saber cuanto
 *       trabajara todavia el empleado) pero se cuentan en
 *       {@code openWorkdays}.</li>
 *   <li>El desglose diario usa los limites de dia natural en la zona IANA
 *       del tenant, no UTC: una jornada que cruza medianoche local, o un dia
 *       de cambio de hora (23h/25h en {@code Europe/Madrid}), se reparte
 *       correctamente entre los dias que toca.</li>
 *   <li>Una jornada se cuenta (para {@code workdayCount}/
 *       {@code adjustedWorkdayCount}/{@code openWorkdays}) en el dia local en
 *       el que <b>empieza</b>, aunque su tiempo trabajado se reparta entre
 *       varios dias.</li>
 * </ul>
 */
public final class TimeSummaryCalculator {

    private TimeSummaryCalculator() {}

    /** Desglose diario para el informe de un empleado, ordenado por dia ascendente. */
    public static List<EmployeeDaySummary> summarizeByDay(List<WorkdayReportEntry> entries, ZoneId zone) {
        Objects.requireNonNull(entries, "entries no puede ser null");
        Objects.requireNonNull(zone, "zone no puede ser null");

        Map<LocalDate, MutableDayBucket> buckets = new TreeMap<>();
        for (WorkdayReportEntry entry : entries) {
            accumulate(entry, zone, buckets);
        }

        List<EmployeeDaySummary> result = new ArrayList<>();
        for (Map.Entry<LocalDate, MutableDayBucket> bucketEntry : buckets.entrySet()) {
            MutableDayBucket bucket = bucketEntry.getValue();
            result.add(new EmployeeDaySummary(
                    bucketEntry.getKey(),
                    nonNegative(bucket.worked),
                    nonNegative(bucket.paused),
                    nonNegative(bucket.expected),
                    nonNegative(bucket.effectiveWorked),
                    nonNegative(bucket.overtime),
                    nonNegative(bucket.deviation),
                    bucket.workdayCount,
                    bucket.adjustedWorkdayCount,
                    bucket.openWorkdays,
                    bucket.evaluatedWorkdayCount));
        }
        return result;
    }

    /** Totales por empleado en todo el rango, sin desglose diario (no depende de zona horaria: ver Javadoc de {@link TenantEmployeeSummary}). */
    public static List<TenantEmployeeSummary> summarizeTotalsByEmployee(List<WorkdayReportEntry> entries) {
        Objects.requireNonNull(entries, "entries no puede ser null");

        Map<UUID, MutableDayBucket> byEmployee = new LinkedHashMap<>();
        for (WorkdayReportEntry entry : entries) {
            MutableDayBucket bucket = byEmployee.computeIfAbsent(entry.employeeId(), id -> new MutableDayBucket());
            if (entry.open()) {
                bucket.openWorkdays++;
                continue;
            }
            Duration total = Duration.between(entry.startedAt(), entry.endedAt());
            Duration breaksDuration = entry.breaks().stream()
                    .map(b -> Duration.between(b.startedAt(), b.endedAt()))
                    .reduce(Duration.ZERO, Duration::plus);
            bucket.worked = bucket.worked.plus(total.minus(breaksDuration));
            bucket.paused = bucket.paused.plus(breaksDuration);
            if (entry.evaluated()) {
                bucket.expected = bucket.expected.plus(entry.expected());
                bucket.effectiveWorked = bucket.effectiveWorked.plus(entry.effectiveWorked());
                bucket.overtime = bucket.overtime.plus(entry.overtime());
                bucket.deviation = bucket.deviation.plus(entry.deviation());
                bucket.evaluatedWorkdayCount++;
            }
            bucket.workdayCount++;
            if (entry.adjusted()) {
                bucket.adjustedWorkdayCount++;
            }
        }

        return byEmployee.entrySet().stream()
                .map(e -> new TenantEmployeeSummary(
                        e.getKey(),
                        nonNegative(e.getValue().worked),
                        nonNegative(e.getValue().paused),
                        nonNegative(e.getValue().expected),
                        nonNegative(e.getValue().effectiveWorked),
                        nonNegative(e.getValue().overtime),
                        nonNegative(e.getValue().deviation),
                        e.getValue().workdayCount,
                        e.getValue().adjustedWorkdayCount,
                        e.getValue().openWorkdays,
                        e.getValue().evaluatedWorkdayCount))
                .sorted(Comparator.comparing(TenantEmployeeSummary::employeeId))
                .toList();
    }

    private static void accumulate(WorkdayReportEntry entry, ZoneId zone, Map<LocalDate, MutableDayBucket> buckets) {
        LocalDate startDay = ZonedDateTime.ofInstant(entry.startedAt(), zone).toLocalDate();
        MutableDayBucket startBucket = buckets.computeIfAbsent(startDay, d -> new MutableDayBucket());

        if (entry.open()) {
            startBucket.openWorkdays++;
            return;
        }

        startBucket.workdayCount++;
        if (entry.adjusted()) {
            startBucket.adjustedWorkdayCount++;
        }
        if (entry.evaluated()) {
            startBucket.evaluatedWorkdayCount++;
        }

        for (DaySegment segment : splitByLocalDay(entry.startedAt(), entry.endedAt(), zone)) {
            MutableDayBucket bucket = buckets.computeIfAbsent(segment.day(), d -> new MutableDayBucket());
            bucket.worked = bucket.worked.plus(segment.duration());
            if (entry.evaluated()) {
                bucket.expected = bucket.expected.plus(allocate(entry.expected(), segment, entry));
                bucket.effectiveWorked = bucket.effectiveWorked.plus(allocate(entry.effectiveWorked(), segment, entry));
                bucket.overtime = bucket.overtime.plus(allocate(entry.overtime(), segment, entry));
                bucket.deviation = bucket.deviation.plus(allocate(entry.deviation(), segment, entry));
            }
        }

        for (BreakInterval breakInterval : entry.breaks()) {
            for (DaySegment segment : splitByLocalDay(breakInterval.startedAt(), breakInterval.endedAt(), zone)) {
                MutableDayBucket bucket = buckets.computeIfAbsent(segment.day(), d -> new MutableDayBucket());
                bucket.paused = bucket.paused.plus(segment.duration());
                bucket.worked = bucket.worked.minus(segment.duration());
            }
        }
    }

    /**
     * Divide el intervalo {@code [start, end)} en segmentos que no cruzan un
     * limite de dia natural en {@code zone}. Usa {@link LocalDate#atStartOfDay(ZoneId)}
     * para calcular cada limite, que recalcula el offset UTC en cada punto y
     * por tanto es correcto en dias de cambio de hora (23h/25h).
     */
    private static List<DaySegment> splitByLocalDay(Instant start, Instant end, ZoneId zone) {
        List<DaySegment> segments = new ArrayList<>();
        ZonedDateTime cursor = start.atZone(zone);
        ZonedDateTime endZoned = end.atZone(zone);
        while (cursor.isBefore(endZoned)) {
            LocalDate day = cursor.toLocalDate();
            ZonedDateTime nextDayStart = day.plusDays(1).atStartOfDay(zone);
            ZonedDateTime segmentEnd = nextDayStart.isBefore(endZoned) ? nextDayStart : endZoned;
            segments.add(new DaySegment(day, Duration.between(cursor, segmentEnd)));
            cursor = segmentEnd;
        }
        return segments;
    }

    private static Duration nonNegative(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    private static Duration allocate(Duration total, DaySegment segment, WorkdayReportEntry entry) {
        if (total.isZero()) {
            return Duration.ZERO;
        }
        Duration fullSpan = Duration.between(entry.startedAt(), entry.endedAt());
        if (fullSpan.isZero() || fullSpan.isNegative()) {
            return Duration.ZERO;
        }
        double ratio = (double) segment.duration().toMillis() / (double) fullSpan.toMillis();
        long millis = Math.round(total.toMillis() * ratio);
        return Duration.ofMillis(millis);
    }

    private record DaySegment(LocalDate day, Duration duration) {}

    private static final class MutableDayBucket {
        private Duration worked = Duration.ZERO;
        private Duration paused = Duration.ZERO;
        private Duration expected = Duration.ZERO;
        private Duration effectiveWorked = Duration.ZERO;
        private Duration overtime = Duration.ZERO;
        private Duration deviation = Duration.ZERO;
        private int workdayCount;
        private int adjustedWorkdayCount;
        private int openWorkdays;
        private int evaluatedWorkdayCount;
    }
}
