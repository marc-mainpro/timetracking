package com.tfp.timetracking.corrections.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.corrections.domain.event.CorrectionApproved;
import com.tfp.timetracking.corrections.domain.event.CorrectionRejected;
import com.tfp.timetracking.corrections.domain.event.CorrectionRequested;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CorrectionsIntegrationEventMapperTest {

    @Test
    void mapsCorrectionRequestedToIntegrationEventWithFullEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID correctionId = UUID.randomUUID();
        UUID workdayId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-20T09:00:00Z");
        CorrectionRequested domainEvent =
                new CorrectionRequested(eventId, occurredAt, tenantId, correctionId, workdayId, requestedBy);

        Optional<IntegrationEvent> mapped = CorrectionsIntegrationEventMapper.map(domainEvent);

        assertThat(mapped).isPresent();
        IntegrationEvent event = mapped.orElseThrow();
        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.eventType()).isEqualTo("corrections.correction-requested.v1");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.aggregateId()).isEqualTo(correctionId);
        assertThat(event.aggregateType()).isEqualTo("CorrectionRequest");
        assertThat(event.payload())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("correctionId", correctionId, "workdayId", workdayId, "requestedBy", requestedBy));
    }

    @Test
    void mapsCorrectionApprovedToIntegrationEventWithFullEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID correctionId = UUID.randomUUID();
        UUID workdayId = UUID.randomUUID();
        UUID resolvedBy = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-20T09:00:00Z");
        CorrectionApproved domainEvent =
                new CorrectionApproved(eventId, occurredAt, tenantId, correctionId, workdayId, resolvedBy);

        Optional<IntegrationEvent> mapped = CorrectionsIntegrationEventMapper.map(domainEvent);

        assertThat(mapped).isPresent();
        IntegrationEvent event = mapped.orElseThrow();
        assertThat(event.eventType()).isEqualTo("corrections.correction-approved.v1");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.aggregateType()).isEqualTo("CorrectionRequest");
        assertThat(event.payload())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("correctionId", correctionId, "workdayId", workdayId, "resolvedBy", resolvedBy));
    }

    @Test
    void mapsCorrectionRejectedToIntegrationEventWithFullEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID correctionId = UUID.randomUUID();
        UUID workdayId = UUID.randomUUID();
        UUID resolvedBy = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-20T09:00:00Z");
        CorrectionRejected domainEvent =
                new CorrectionRejected(eventId, occurredAt, tenantId, correctionId, workdayId, resolvedBy);

        Optional<IntegrationEvent> mapped = CorrectionsIntegrationEventMapper.map(domainEvent);

        assertThat(mapped).isPresent();
        IntegrationEvent event = mapped.orElseThrow();
        assertThat(event.eventType()).isEqualTo("corrections.correction-rejected.v1");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.aggregateType()).isEqualTo("CorrectionRequest");
        assertThat(event.payload())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("correctionId", correctionId, "workdayId", workdayId, "resolvedBy", resolvedBy));
    }

    @Test
    void ignoresUnknownDomainEvents() {
        assertThat(CorrectionsIntegrationEventMapper.map(new Object())).isEmpty();
    }
}
