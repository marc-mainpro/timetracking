package com.tfp.timetracking.reporting.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * Proyeccion JPQL minima de {@code break_entry} (solo pausas cerradas) para
 * el calculo de informes (T801).
 */
public record ReportBreakRow(UUID workdayId, Instant startedAt, Instant endedAt) {}
