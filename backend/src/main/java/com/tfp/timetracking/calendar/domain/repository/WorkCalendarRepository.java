package com.tfp.timetracking.calendar.domain.repository;

import com.tfp.timetracking.calendar.domain.model.CalendarStatus;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de dominio para persistir y recuperar {@link WorkCalendar}
 * (CONTEXT-GLOBAL §4: puertos en dominio, adaptadores en infraestructura).
 *
 * <p>Toda consulta lleva {@code tenantId} como <b>primer parametro</b>
 * (ADR-0002): el aislamiento no se delega en el llamante. Una lectura de otro
 * tenant devuelve {@link Optional#empty()} —nunca el agregado ajeno—, de modo
 * que el caso de uso responda 404 y no filtre la existencia del recurso.
 */
public interface WorkCalendarRepository {

    WorkCalendar save(WorkCalendar calendar);

    Optional<WorkCalendar> findById(UUID tenantId, UUID id);

    /** Calendarios del tenant con esos ids, en cualquier estado. Ignora los ids ajenos. */
    List<WorkCalendar> findAllByIds(UUID tenantId, Collection<UUID> ids);

    /** {@code true} si el tenant ya tiene un calendario con ese nombre (comparacion exacta). */
    boolean existsByName(UUID tenantId, String name);

    /**
     * Listado paginado por tenant, ordenado por nombre ascendente.
     *
     * @param status filtro opcional por estado; {@code null} devuelve todos
     */
    PagedResult<WorkCalendar> findByTenant(UUID tenantId, CalendarStatus status, int page, int size);
}
