package com.tfp.timetracking.identity.application.integration;

import com.tfp.timetracking.identity.domain.event.PasswordResetRequested;
import com.tfp.timetracking.shared.application.IntegrationEventMapper;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetIntegrationEventMapper implements IntegrationEventMapper {

    @Override
    public Optional<IntegrationEvent> map(Object domainEvent) {
        if (!(domainEvent instanceof PasswordResetRequested event)) {
            return Optional.empty();
        }
        return Optional.of(new IntegrationEvent(
                event.eventId(),
                "identity.password-reset-requested.v1",
                1,
                event.occurredAt(),
                event.tenantId(),
                event.aggregateId(),
                "PasswordResetToken",
                Map.of(
                        "userId", event.userId(),
                        "email", event.email(),
                        "firstName", event.firstName(),
                        "resetToken", event.resetToken(),
                        "expiresAt", event.expiresAt().toString())));
    }
}
