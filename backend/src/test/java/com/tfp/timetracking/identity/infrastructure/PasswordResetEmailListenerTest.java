package com.tfp.timetracking.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.identity.application.PasswordResetProperties;
import com.tfp.timetracking.notification.application.EmailMessage;
import com.tfp.timetracking.notification.application.EmailSender;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordResetEmailListenerTest {

    private final List<EmailMessage> sent = new ArrayList<>();
    private final EmailSender emailSender = sent::add;
    private final PasswordResetProperties properties =
            new PasswordResetProperties(Duration.ofHours(1), "https://app.test/restablecer?token=%s");
    private final PasswordResetEmailListener listener = new PasswordResetEmailListener(emailSender, properties);

    @Test
    void sendsResetLink() {
        listener.onEvent(event(fullPayload()));

        assertThat(sent).singleElement().satisfies(message -> {
            assertThat(message.to()).isEqualTo("jane@example.com");
            assertThat(message.subject()).isEqualTo("Restablece tu contrasena");
            assertThat(message.body()).contains("https://app.test/restablecer?token=raw-token");
            assertThat(message.body()).contains("Hola Jane");
        });
    }

    @Test
    void skipsIncompleteEvents() {
        Map<String, Object> payload = fullPayload();
        payload.remove("resetToken");

        listener.onEvent(event(payload));

        assertThat(sent).isEmpty();
    }

    private IntegrationEvent event(Map<String, Object> payload) {
        return new IntegrationEvent(
                UUID.randomUUID(),
                PasswordResetEmailListener.EVENT_TYPE,
                1,
                Instant.parse("2026-08-05T10:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PasswordResetToken",
                payload);
    }

    private Map<String, Object> fullPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "jane@example.com");
        payload.put("firstName", "Jane");
        payload.put("resetToken", "raw-token");
        return payload;
    }
}
