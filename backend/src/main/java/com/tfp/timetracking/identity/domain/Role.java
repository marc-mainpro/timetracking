package com.tfp.timetracking.identity.domain;

/**
 * Roles de negocio (CONTEXT-GLOBAL §6): {@code TENANT_ADMIN} gestiona
 * empleados, correcciones, informes y auditoria; {@code EMPLOYEE} gestiona su
 * propia jornada, historial y correcciones.
 */
public enum Role {
    TENANT_ADMIN,
    EMPLOYEE
}
