package com.tfp.timetracking.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.notification.application.EmailMessage;
import com.tfp.timetracking.notification.application.EmailSender;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.tenant.application.RegistrationProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.tfp.timetracking.outbox.application.ProcessedEventStore;
import org.junit.jupiter.api.Test;

/** T53-05 / ADR-0012: el correo se envía desde el consumidor del outbox, con el enlace correcto. */
class TenantRegistrationEmailListenerTest {

    private final List<EmailMessage> sent = new ArrayList<>();
    private final EmailSender emailSender = sent::add;

    private final RegistrationProperties properties = new RegistrationProperties(
            new RegistrationProperties.Verification(Duration.ofHours(24), 3),
            new RegistrationProperties.Throttle(Duration.ofHours(1), 5, 3),
            "https://app.test/registro/verificar?token=%s",
            "");

    private final TenantRegistrationEmailListener listener =
            new TenantRegistrationEmailListener(alwaysClaims(), emailSender, properties);

    private IntegrationEvent verificationEvent(Map<String, Object> payload) {
        return new IntegrationEvent(
                UUID.randomUUID(),
                TenantRegistrationEmailListener.EVENT_TYPE,
                1,
                Instant.parse("2026-08-01T10:00:00Z"),
                PlatformTenant.ID,
                UUID.randomUUID(),
                "TenantRegistration",
                payload);
    }

    private static Map<String, Object> fullPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "owner@acme.test");
        payload.put("ownerFirstName", "Jane");
        payload.put("verificationToken", "raw-token");
        return payload;
    }

    @Test
    void sendsTheVerificationLinkToTheApplicant() {
        listener.onEvent(verificationEvent(fullPayload()));

        assertThat(sent).singleElement().satisfies(message -> {
            assertThat(message.to()).isEqualTo("owner@acme.test");
            assertThat(message.subject()).isEqualTo("Confirma tu dirección de correo");
            assertThat(message.body()).contains("https://app.test/registro/verificar?token=raw-token");
            assertThat(message.body()).contains("Hola Jane");
            assertThat(message.body()).contains("24 horas");
        });
    }

    @Test
    void greetsGenericallyWhenTheNameIsMissing() {
        Map<String, Object> payload = fullPayload();
        payload.remove("ownerFirstName");

        listener.onEvent(verificationEvent(payload));

        assertThat(sent).singleElement().satisfies(message -> assertThat(message.body()).startsWith("Hola,"));
    }

    @Test
    void ignoresEventsOfOtherTypes() {
        listener.onEvent(new IntegrationEvent(
                UUID.randomUUID(),
                "tenant.registered.v1",
                1,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Tenant",
                Map.of()));

        assertThat(sent).isEmpty();
    }

    @Test
    void skipsAnIncompleteEventInsteadOfFailing() {
        Map<String, Object> withoutToken = fullPayload();
        withoutToken.remove("verificationToken");
        listener.onEvent(verificationEvent(withoutToken));

        Map<String, Object> withoutEmail = fullPayload();
        withoutEmail.remove("email");
        listener.onEvent(verificationEvent(withoutEmail));

        assertThat(sent).isEmpty();
    }

    /** Reserva siempre concedida: la idempotencia se prueba aparte. */
    private static ProcessedEventStore alwaysClaims() {
        return (eventId, consumer) -> true;
    }
}
