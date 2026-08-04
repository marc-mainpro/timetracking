package com.tfp.timetracking.calendar.application.command;

import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import java.util.UUID;

/**
 * Asignacion de un calendario a un ambito (RF-CAL-006, T70-02).
 *
 * @param targetId equipo o empleado destinatario; {@code null} en ambito
 *     {@code TENANT}. En ambito {@code TEAM} es un identificador opaco: no hay
 *     gestion de equipos en el sistema (ADR-0017)
 */
public record AssignCalendarCommand(UUID calendarId, AssignmentScope scope, UUID targetId) {}
