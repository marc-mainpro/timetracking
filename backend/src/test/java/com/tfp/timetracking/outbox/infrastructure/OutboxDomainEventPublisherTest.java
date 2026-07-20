package com.tfp.timetracking.outbox.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.tfp.timetracking.outbox.application.OutboxWriter;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.tenant.domain.event.TenantRegistered;
import com.tfp.timetracking.timetracking.domain.event.BreakStarted;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxDomainEventPublisherTest {

    @Test
    void writesIntegrationEventForKnownDomainEvent() {
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        OutboxDomainEventPublisher publisher = new OutboxDomainEventPublisher(outboxWriter);
        UUID tenantId = UUID.randomUUID();
        TenantRegistered event =
                new TenantRegistered(UUID.randomUUID(), Instant.now(), tenantId, tenantId, "Acme", "Europe/Madrid");

        publisher.publish(List.of(event));

        verify(outboxWriter, times(1)).write(any(IntegrationEvent.class));
    }

    @Test
    void skipsDomainEventsWithoutIntegrationTranslation() {
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        OutboxDomainEventPublisher publisher = new OutboxDomainEventPublisher(outboxWriter);
        BreakStarted event = new BreakStarted(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        publisher.publish(List.of(event));

        verify(outboxWriter, never()).write(any());
    }

    @Test
    void handlesEmptyEventList() {
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        OutboxDomainEventPublisher publisher = new OutboxDomainEventPublisher(outboxWriter);

        publisher.publish(List.of());

        verify(outboxWriter, never()).write(any());
    }
}
