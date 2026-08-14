package com.tfp.timetracking.absence.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.absence.domain.event.AbsenceApproved;
import com.tfp.timetracking.absence.domain.event.AbsenceCancelled;
import com.tfp.timetracking.absence.domain.event.AbsenceRequested;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AbsenceIntegrationEventMapperTest {

    private static final AbsenceIntegrationEventMapper MAPPER = new AbsenceIntegrationEventMapper();
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-13T10:00:00Z");

    @Test
    void mapsARequestedAbsence() {
        // T170-06: la solicitud se publica desde que existe un consumidor —el
        // aviso al administrador que debe resolverla—.
        UUID tenantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID absenceTypeId = UUID.randomUUID();

        IntegrationEvent event = MAPPER.map(new AbsenceRequested(
                        UUID.randomUUID(),
                        OCCURRED_AT,
                        tenantId,
                        requestId,
                        employeeId,
                        absenceTypeId,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 5)))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("absence.absence-requested.v1");
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.aggregateType()).isEqualTo("AbsenceRequest");
        assertThat(event.payload())
                .containsEntry("absenceRequestId", requestId)
                .containsEntry("employeeId", employeeId)
                .containsEntry("absenceTypeId", absenceTypeId)
                .containsEntry("startDate", "2026-09-01")
                .containsEntry("endDate", "2026-09-05");
    }

    @Test
    void mapsAnApprovedAbsence() {
        UUID employeeId = UUID.randomUUID();

        IntegrationEvent event = MAPPER.map(new AbsenceApproved(
                        UUID.randomUUID(),
                        OCCURRED_AT,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        employeeId,
                        UUID.randomUUID()))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("absence.absence-approved.v1");
        assertThat(event.payload()).containsEntry("employeeId", employeeId);
    }

    @Test
    void doesNotPublishACancellation() {
        // La hace el propio empleado, que ya sabe que ha ocurrido: sigue siendo
        // contrato sin consumidor.
        assertThat(MAPPER.map(new AbsenceCancelled(
                        UUID.randomUUID(), OCCURRED_AT, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .isEqualTo(Optional.empty());
    }
}
