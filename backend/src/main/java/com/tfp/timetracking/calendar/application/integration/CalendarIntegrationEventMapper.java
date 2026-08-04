package com.tfp.timetracking.calendar.application.integration;

import com.tfp.timetracking.calendar.domain.event.CalendarAssigned;
import com.tfp.timetracking.calendar.domain.event.CalendarAssignmentRemoved;
import com.tfp.timetracking.calendar.domain.event.WorkCalendarArchived;
import com.tfp.timetracking.calendar.domain.event.WorkCalendarCreated;
import com.tfp.timetracking.calendar.domain.event.WorkCalendarUpdated;
import com.tfp.timetracking.shared.application.IntegrationEventMapper;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Traduce los eventos de dominio del modulo {@code calendar} a eventos de
 * integracion versionados {@code calendar.<hecho>.v1} (ADR-0005, ADR-0011).
 *
 * <p>Se da de alta solo por ser un {@code @Component}: {@code
 * OutboxDomainEventPublisher} recibe todos los {@link IntegrationEventMapper}
 * inyectados y no hay que editarlo.
 *
 * <p><b>Fechas locales en el payload.</b> {@code validFrom}/{@code validTo} se
 * serializan como {@code YYYY-MM-DD} (ISO-8601 de fecha, sin zona) porque la
 * vigencia de un calendario es un periodo civil y no un intervalo de instantes
 * (RNF-011). Convertirlas a {@code Instant} obligaria a elegir una hora
 * arbitraria y haria que el mismo calendario "cambiara de dia" segun la zona del
 * consumidor.
 */
@Component
public class CalendarIntegrationEventMapper implements IntegrationEventMapper {

    private static final String CALENDAR_AGGREGATE = "WorkCalendar";
    private static final String ASSIGNMENT_AGGREGATE = "CalendarAssignment";

    @Override
    public Optional<IntegrationEvent> map(Object domainEvent) {
        if (domainEvent instanceof WorkCalendarCreated event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "calendar.calendar-created.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    CALENDAR_AGGREGATE,
                    calendarPayload(
                            event.aggregateId(),
                            event.name(),
                            event.timezone(),
                            event.validFrom(),
                            event.validTo())));
        }
        if (domainEvent instanceof WorkCalendarUpdated event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "calendar.calendar-updated.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    CALENDAR_AGGREGATE,
                    calendarPayload(
                            event.aggregateId(),
                            event.name(),
                            event.timezone(),
                            event.validFrom(),
                            event.validTo())));
        }
        if (domainEvent instanceof WorkCalendarArchived event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "calendar.calendar-archived.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    CALENDAR_AGGREGATE,
                    Map.of("calendarId", event.aggregateId(), "name", event.name())));
        }
        if (domainEvent instanceof CalendarAssigned event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "calendar.calendar-assigned.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    ASSIGNMENT_AGGREGATE,
                    assignmentPayload(
                            event.aggregateId(), event.calendarId(), event.scope().name(), event.targetId())));
        }
        if (domainEvent instanceof CalendarAssignmentRemoved event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "calendar.calendar-assignment-removed.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    ASSIGNMENT_AGGREGATE,
                    assignmentPayload(
                            event.aggregateId(), event.calendarId(), event.scope().name(), event.targetId())));
        }
        return Optional.empty();
    }

    private Map<String, Object> calendarPayload(
            UUID calendarId, String name, String timezone, LocalDate validFrom, LocalDate validTo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("calendarId", calendarId);
        payload.put("name", name);
        payload.put("timezone", timezone);
        payload.put("validFrom", validFrom.toString());
        if (validTo != null) {
            payload.put("validTo", validTo.toString());
        }
        return payload;
    }

    private Map<String, Object> assignmentPayload(UUID assignmentId, UUID calendarId, String scope, UUID targetId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignmentId", assignmentId);
        payload.put("calendarId", calendarId);
        payload.put("scope", scope);
        if (targetId != null) {
            payload.put("targetId", targetId);
        }
        return payload;
    }
}
