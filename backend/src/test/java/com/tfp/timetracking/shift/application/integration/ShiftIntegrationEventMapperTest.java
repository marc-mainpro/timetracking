package com.tfp.timetracking.shift.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.shift.domain.event.ShiftAssigned;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShiftIntegrationEventMapperTest {

    private static final ShiftIntegrationEventMapper MAPPER = new ShiftIntegrationEventMapper();
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-13T10:00:00Z");

    @Test
    void mapsAnAssignedShift() {
        UUID tenantId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();

        IntegrationEvent event = MAPPER.map(new ShiftAssigned(
                        UUID.randomUUID(),
                        OCCURRED_AT,
                        tenantId,
                        assignmentId,
                        employeeId,
                        templateId,
                        "General",
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30)))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("shift.shift-assigned.v1");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.aggregateId()).isEqualTo(assignmentId);
        assertThat(event.aggregateType()).isEqualTo("ShiftAssignment");
        assertThat(event.payload())
                .containsEntry("shiftAssignmentId", assignmentId)
                .containsEntry("employeeId", employeeId)
                .containsEntry("shiftTemplateId", templateId)
                .containsEntry("shiftTemplateName", "General")
                .containsEntry("validFrom", "2026-09-01")
                .containsEntry("validTo", "2026-09-30");
    }

    @Test
    void omitsTheEndDateOfAnOpenEndedAssignment() {
        // La vigencia indefinida se representa con la ausencia del campo, no
        // con una fecha centinela que el consumidor tendria que interpretar.
        IntegrationEvent event = MAPPER.map(new ShiftAssigned(
                        UUID.randomUUID(),
                        OCCURRED_AT,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "General",
                        LocalDate.of(2026, 9, 1),
                        null))
                .orElseThrow();

        assertThat(event.payload()).doesNotContainKey("validTo");
    }

    @Test
    void ignoresDomainEventsWithoutAnIntegrationContract() {
        assertThat(MAPPER.map(new Object())).isEqualTo(Optional.empty());
    }
}
