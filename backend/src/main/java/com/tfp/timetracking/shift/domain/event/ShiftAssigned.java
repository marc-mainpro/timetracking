package com.tfp.timetracking.shift.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento de dominio: se ha asignado un turno a un empleado (T170-05).
 *
 * <p>Es el primer hecho que el modulo {@code shift} publica hacia fuera. Lo
 * consume {@code notification} para avisar al empleado, que hasta ahora solo se
 * enteraba de su turno si entraba a mirarlo.
 *
 * @param eventId identificador unico del evento
 * @param occurredAt instante en el que ocurrio el hecho (reloj de dominio)
 * @param tenantId tenant al que pertenece la asignacion
 * @param aggregateId identificador de la asignacion
 * @param employeeId empleado al que se le asigna el turno
 * @param shiftTemplateId plantilla de turno asignada
 * @param shiftTemplateName nombre de la plantilla <b>en el momento de asignar</b>.
 *     Viaja en el evento porque el aviso al empleado tiene que decir qué turno
 *     es, y el consumidor no puede preguntarlo sin abrir una dependencia nueva
 *     hacia este módulo. Es una foto del momento: si la plantilla se renombra
 *     después, el aviso ya emitido no debe cambiar —el mismo criterio que
 *     aplica {@code Notification.recipientEmail}—
 * @param validFrom primer dia de vigencia
 * @param validTo ultimo dia de vigencia, o {@code null} si es indefinida
 */
public record ShiftAssigned(
        UUID eventId,
        Instant occurredAt,
        UUID tenantId,
        UUID aggregateId,
        UUID employeeId,
        UUID shiftTemplateId,
        String shiftTemplateName,
        LocalDate validFrom,
        LocalDate validTo) {}
