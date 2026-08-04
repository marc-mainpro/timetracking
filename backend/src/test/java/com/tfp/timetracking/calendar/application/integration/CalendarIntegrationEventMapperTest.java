package com.tfp.timetracking.calendar.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.calendar.domain.event.CalendarAssigned;
import com.tfp.timetracking.calendar.domain.event.CalendarAssignmentRemoved;
import com.tfp.timetracking.calendar.domain.event.WorkCalendarArchived;
import com.tfp.timetracking.calendar.domain.event.WorkCalendarCreated;
import com.tfp.timetracking.calendar.domain.event.WorkCalendarUpdated;
import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Traduccion a eventos de integracion {@code calendar.*.v1} (ADR-0005, ADR-0011). */
class CalendarIntegrationEventMapperTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CALENDAR_ID = UUID.randomUUID();
    private static final UUID ASSIGNMENT_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-15T09:00:00Z");
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    private final CalendarIntegrationEventMapper mapper = new CalendarIntegrationEventMapper();

    @Test
    void mapsCalendarCreated() {
        IntegrationEvent event = mapper.map(new WorkCalendarCreated(
                        EVENT_ID, OCCURRED_AT, TENANT_ID, CALENDAR_ID, "General", "Europe/Madrid", FROM, TO))
                .orElseThrow();

        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.eventType()).isEqualTo("calendar.calendar-created.v1");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.aggregateId()).isEqualTo(CALENDAR_ID);
        assertThat(event.aggregateType()).isEqualTo("WorkCalendar");
        assertThat(event.payload())
                .containsEntry("calendarId", CALENDAR_ID)
                .containsEntry("name", "General")
                .containsEntry("timezone", "Europe/Madrid")
                // Fechas locales serializadas como YYYY-MM-DD, no como instantes.
                .containsEntry("validFrom", "2026-01-01")
                .containsEntry("validTo", "2026-12-31");
    }

    @Test
    void omitsValidToWhenValidityIsOpenEnded() {
        IntegrationEvent event = mapper.map(new WorkCalendarUpdated(
                        EVENT_ID, OCCURRED_AT, TENANT_ID, CALENDAR_ID, "General", "UTC", FROM, null))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("calendar.calendar-updated.v1");
        assertThat(event.payload()).containsEntry("validFrom", "2026-01-01").doesNotContainKey("validTo");
    }

    @Test
    void mapsCalendarArchived() {
        IntegrationEvent event = mapper.map(
                        new WorkCalendarArchived(EVENT_ID, OCCURRED_AT, TENANT_ID, CALENDAR_ID, "General"))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("calendar.calendar-archived.v1");
        assertThat(event.payload()).containsEntry("calendarId", CALENDAR_ID).containsEntry("name", "General");
    }

    @Test
    void mapsCalendarAssignedWithTarget() {
        IntegrationEvent event = mapper.map(new CalendarAssigned(
                        EVENT_ID,
                        OCCURRED_AT,
                        TENANT_ID,
                        ASSIGNMENT_ID,
                        CALENDAR_ID,
                        AssignmentScope.EMPLOYEE,
                        TARGET_ID))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("calendar.calendar-assigned.v1");
        assertThat(event.aggregateId()).isEqualTo(ASSIGNMENT_ID);
        assertThat(event.aggregateType()).isEqualTo("CalendarAssignment");
        assertThat(event.payload())
                .containsEntry("assignmentId", ASSIGNMENT_ID)
                .containsEntry("calendarId", CALENDAR_ID)
                .containsEntry("scope", "EMPLOYEE")
                .containsEntry("targetId", TARGET_ID);
    }

    @Test
    void omitsTargetForTenantScopedAssignments() {
        IntegrationEvent event = mapper.map(new CalendarAssignmentRemoved(
                        EVENT_ID, OCCURRED_AT, TENANT_ID, ASSIGNMENT_ID, CALENDAR_ID, AssignmentScope.TENANT, null))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("calendar.calendar-assignment-removed.v1");
        assertThat(event.payload()).containsEntry("scope", "TENANT").doesNotContainKey("targetId");
    }

    @Test
    void ignoresEventsOfOtherModules() {
        assertThat(mapper.map("no soy un evento de calendario")).isEqualTo(Optional.empty());
        assertThat(mapper.map(new Object())).isEmpty();
    }
}
