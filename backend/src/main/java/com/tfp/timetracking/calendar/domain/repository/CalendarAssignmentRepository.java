package com.tfp.timetracking.calendar.domain.repository;

import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignment;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de dominio para {@link CalendarAssignment} (T70-02, RF-CAL-006).
 * {@code tenantId} es siempre el primer parametro (ADR-0002).
 */
public interface CalendarAssignmentRepository {

    CalendarAssignment save(CalendarAssignment assignment);

    Optional<CalendarAssignment> findById(UUID tenantId, UUID id);

    /**
     * Asignacion existente para ese ambito y destinatario, si la hay. Sirve para
     * rechazar duplicados antes de tocar el indice unico.
     *
     * @param targetId {@code null} en ambito {@code TENANT}
     */
    Optional<CalendarAssignment> findByScope(UUID tenantId, AssignmentScope scope, UUID targetId);

    /**
     * Asignaciones candidatas para resolver el calendario efectivo de un
     * empleado: todas las de ambito {@code TENANT}, las de ambito {@code TEAM}
     * cuyo destinatario sea {@code teamId} (si no es nulo) y la de ambito
     * {@code EMPLOYEE} de ese empleado.
     *
     * <p>El filtrado fino y la precedencia los aplica
     * {@code EffectiveCalendarResolver}: aqui solo se acota la lectura para no
     * traerse todas las asignaciones del tenant.
     *
     * @param teamId equipo del empleado aportado por el llamante; {@code null}
     *     excluye las asignaciones de equipo
     */
    List<CalendarAssignment> findApplicable(UUID tenantId, UUID employeeId, UUID teamId);

    /**
     * Listado paginado por tenant, ordenado por ambito descendente (mas
     * especifico primero) y luego por fecha de alta.
     *
     * @param calendarId filtro opcional por calendario; {@code null} devuelve todas
     */
    PagedResult<CalendarAssignment> findByTenant(UUID tenantId, UUID calendarId, int page, int size);

    /** Borra la asignacion si pertenece al tenant. No falla si no existe. */
    void delete(UUID tenantId, UUID id);
}
