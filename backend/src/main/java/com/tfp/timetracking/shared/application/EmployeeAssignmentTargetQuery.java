package com.tfp.timetracking.shared.application;

import java.util.UUID;

/**
 * Responde si un usuario admite las asignaciones propias de un empleado
 * (turnos, calendarios de ambito empleado).
 *
 * <p>Lo sabe {@code identity} —es quien tiene usuarios y roles— y lo consumen
 * {@code shift} y {@code calendar}, que no acceden al repositorio de otro
 * modulo (AGENTS.md, ADR-0001).
 *
 * <p>El puerto vive en {@code shared} por el mismo criterio que
 * {@link TenantUsageQuery}: lo preguntan <b>dos</b> modulos, asi que declararlo
 * en cada uno duplicaria el contrato, y declararlo en {@code identity} le
 * obligaria a mirar hacia quien le pregunta (ADR-0011).
 *
 * <p>Una sola operacion, y no un «existe» mas un «es empleado»: las dos
 * respuestas llevan a codigos HTTP distintos (404 y 409) y separarlas en dos
 * consultas abriria una carrera entre ambas.
 */
public interface EmployeeAssignmentTargetQuery {

    /**
     * @param tenantId tenant desde el que se pregunta; un usuario de otro
     *     tenant se responde como {@link TargetStatus#UNKNOWN} aunque el id
     *     exista, para no filtrar su existencia
     */
    TargetStatus check(UUID tenantId, UUID userId);

    enum TargetStatus {
        /** No hay tal usuario en este tenant. */
        UNKNOWN,
        /** Existe, pero no tiene el rol de empleado. */
        NOT_EMPLOYEE,
        /** Existe y admite asignaciones de empleado. */
        ASSIGNABLE
    }
}
