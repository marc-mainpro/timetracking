package com.tfp.timetracking.reporting.domain;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de dominio para resolver el nombre de los empleados de un tenant.
 *
 * <p>{@link TenantEmployeeSummary} se calcula a partir de jornadas y no
 * conoce nombres de empleado (CONTEXT-DOMINIO: la agregacion de tiempo es un
 * concepto puro de {@code reporting}, ajeno a {@code identity}). Este puerto
 * es el unico punto donde {@code reporting} cruza esa frontera, y solo lo
 * hacen los formateadores de salida (CSV, PDF) que necesitan un dato legible
 * para quien abre el fichero.
 */
public interface EmployeeDirectoryPort {

    Map<UUID, EmployeeName> namesByTenant(UUID tenantId);
}
