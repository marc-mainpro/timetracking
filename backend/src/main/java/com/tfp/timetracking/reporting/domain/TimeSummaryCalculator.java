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
                    bucket.workdayCount,
                    bucket.adjustedWorkdayCount,
                    bucket.openWorkdays));
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
                        e.getValue().workdayCount,
                        e.getValue().adjustedWorkdayCount,
                        e.getValue().openWorkdays))
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

        for (DaySegment segment : splitByLocalDay(entry.startedAt(), entry.endedAt(), zone)) {
            buckets.computeIfAbsent(segment.day(), d -> new MutableDayBucket()).worked =
                    buckets.get(segment.day()).worked.plus(segment.duration());
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

    private record DaySegment(LocalDate day, Duration duration) {}

    private static final class MutableDayBucket {
        private Duration worked = Duration.ZERO;
        private Duration paused = Duration.ZERO;
        private int workdayCount;
        private int adjustedWorkdayCount;
        private int openWorkdays;
    }
}
