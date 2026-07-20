package com.tfp.timetracking.reporting.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de dominio para consultas de agregacion de informes (T801).
 *
 * <p><b>Decision arquitectonica explicita</b> (documentada tambien en
 * {@code LayeredArchitectureTest} y en {@code tasks/_reports/T801-report.md}):
 * este puerto es deliberadamente distinto de {@code WorkdayRepository}
 * (dominio de {@code timetracking}). {@code WorkdayRepository} reconstruye
 * el agregado {@code Workday} completo (con eventos de dominio, invariantes
 * y paginacion pensada para listados interactivos); usarlo aqui obligaria a
 * paginar sobre potencialmente miles de jornadas por tenant/rango solo para
 * sumar duraciones, cargando datos y objetos que el calculo de informes no
 * necesita.
 *
 * <p>En su lugar, la implementacion de infraestructura
 * ({@code reporting.infrastructure.persistence.WorkdaySummaryQueryPortAdapter})
 * ejecuta una consulta JPQL de proyeccion (solo las columnas necesarias,
 * sin el grafo completo de eventos) directamente contra las tablas
 * {@code workday}/{@code break_entry}, igual que se hizo con el ArchUnit de
 * T403/T602 para los mappers REST: es una excepcion puntual y documentada,
 * no una relajacion general de la regla de capas.
 */
public interface WorkdaySummaryQueryPort {

    List<WorkdayReportEntry> findByEmployee(UUID tenantId, UUID employeeId, Instant from, Instant to);

    List<WorkdayReportEntry> findByTenant(UUID tenantId, Instant from, Instant to);
}
