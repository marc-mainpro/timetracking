package com.tfp.timetracking.identity.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.identity.domain.event.PasswordResetRequested;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordResetIntegrationEventMapperTest {

    private static final PasswordResetIntegrationEventMapper MAPPER = new PasswordResetIntegrationEventMapper();

    @Test
    void mapsPasswordResetRequestedEvent() {
        UUID aggregateId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        IntegrationEvent event = MAPPER.map(new PasswordResetRequested(
                        UUID.randomUUID(),
                        Instant.parse("2026-08-05T10:00:00Z"),
                        tenantId,
                        aggregateId,
                        userId,
                        "jane@example.com",
                        "Jane",
                        "raw-token",
                        Instant.parse("2026-08-05T11:00:00Z")))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("identity.password-reset-requested.v1");
        assertThat(event.aggregateId()).isEqualTo(aggregateId);
        assertThat(event.payload()).containsEntry("userId", userId);
        assertThat(event.payload()).containsEntry("email", "jane@example.com");
        assertThat(event.payload()).containsEntry("resetToken", "raw-token");
    }
}
