package com.tfp.timetracking.reporting.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * Proyeccion JPQL minima de {@code workday} para el calculo de informes
 * (T801). Solo las columnas necesarias, sin cargar el grafo completo del
 * agregado (pausas via {@link ReportBreakRow} en una segunda consulta
 * separada) ni construir el objeto de dominio {@code Workday}.
 */
public record ReportWorkdayRow(UUID id, UUID employeeId, String status, Instant startedAt, Instant endedAt) {}
