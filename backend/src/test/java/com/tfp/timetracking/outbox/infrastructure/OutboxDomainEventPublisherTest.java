package com.tfp.timetracking.outbox.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.tfp.timetracking.corrections.application.integration.CorrectionsIntegrationEventMapper;
import com.tfp.timetracking.identity.application.integration.IdentityIntegrationEventMapper;
import com.tfp.timetracking.outbox.application.OutboxWriter;
import com.tfp.timetracking.shared.application.IntegrationEventMapper;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.tenant.application.integration.TenantIntegrationEventMapper;
import com.tfp.timetracking.tenant.domain.event.TenantRegistered;
import com.tfp.timetracking.timetracking.application.integration.TimeTrackingIntegrationEventMapper;
import com.tfp.timetracking.timetracking.domain.event.BreakStarted;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxDomainEventPublisherTest {

    /** Los mismos mappers que Spring inyectaria como List<IntegrationEventMapper>. */
    private static final List<IntegrationEventMapper> MAPPERS = List.of(
            new TenantIntegrationEventMapper(),
            new IdentityIntegrationEventMapper(),
            new TimeTrackingIntegrationEventMapper(),
            new CorrectionsIntegrationEventMapper());

    @Test
    void writesIntegrationEventForKnownDomainEvent() {
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        OutboxDomainEventPublisher publisher = new OutboxDomainEventPublisher(outboxWriter, MAPPERS);
        UUID tenantId = UUID.randomUUID();
        TenantRegistered event =
                new TenantRegistered(UUID.randomUUID(), Instant.now(), tenantId, tenantId, "Acme", "Europe/Madrid");

        publisher.publish(List.of(event));

        verify(outboxWriter, times(1)).write(any(IntegrationEvent.class));
    }

    @Test
    void skipsDomainEventsWithoutIntegrationTranslation() {
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        OutboxDomainEventPublisher publisher = new OutboxDomainEventPublisher(outboxWriter, MAPPERS);
        BreakStarted event = new BreakStarted(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        publisher.publish(List.of(event));

        verify(outboxWriter, never()).write(any());
    }

    @Test
    void handlesEmptyEventList() {
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        OutboxDomainEventPublisher publisher = new OutboxDomainEventPublisher(outboxWriter, MAPPERS);

        publisher.publish(List.of());

        verify(outboxWriter, never()).write(any());
    }

    @Test
    void skipsEveryEventWhenNoMapperIsContributed() {
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        OutboxDomainEventPublisher publisher = new OutboxDomainEventPublisher(outboxWriter, List.of());
        UUID tenantId = UUID.randomUUID();
        TenantRegistered event =
                new TenantRegistered(UUID.randomUUID(), Instant.now(), tenantId, tenantId, "Acme", "Europe/Madrid");

        publisher.publish(List.of(event));

        verify(outboxWriter, never()).write(any());
    }
}
