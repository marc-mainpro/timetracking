package com.tfp.timetracking.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.tfp.timetracking.notification.application.EmailDeliveryException;
import com.tfp.timetracking.notification.application.EmailMessage;
import com.tfp.timetracking.notification.application.EmailSender;
import com.tfp.timetracking.outbox.application.ProcessedEventStore;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class PasswordResetEmailListenerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("timetracking")
            .withUsername("timetracking")
            .withPassword("timetracking");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PasswordResetEmailListener listener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private EmailSender emailSender;

    @Test
    void releasesTheProcessedEventClaimWhenTheEmailDeliveryFails() {
        IntegrationEvent event = event();
        doThrow(new EmailDeliveryException("SMTP caido", new RuntimeException("boom")))
                .when(emailSender)
                .send(any(EmailMessage.class));

        assertThatThrownBy(() -> listener.onEvent(event)).isInstanceOf(EmailDeliveryException.class);
        assertThat(processedEventClaims(event.eventId(), PasswordResetEmailListener.CONSUMER)).isZero();

        clearInvocations(emailSender);
        doNothing().when(emailSender).send(any(EmailMessage.class));

        listener.onEvent(event);

        verify(emailSender).send(any(EmailMessage.class));
        assertThat(processedEventClaims(event.eventId(), PasswordResetEmailListener.CONSUMER)).isEqualTo(1);
    }

    private long processedEventClaims(UUID eventId, String consumer) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_event WHERE event_id = ? AND consumer = ?",
                Long.class,
                eventId,
                consumer);
    }

    private IntegrationEvent event() {
        return new IntegrationEvent(
                UUID.randomUUID(),
                PasswordResetEmailListener.EVENT_TYPE,
                1,
                Instant.parse("2026-08-05T10:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PasswordResetToken",
                Map.of("email", "jane@example.com", "firstName", "Jane", "resetToken", "raw-token"));
    }
}
