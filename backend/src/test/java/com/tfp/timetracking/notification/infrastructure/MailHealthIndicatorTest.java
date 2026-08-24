package com.tfp.timetracking.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.shared.infrastructure.observability.HealthStatuses;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class MailHealthIndicatorTest {

    @Test
    void reportsDisabledWithoutDraggingTheAggregateDown() {
        Health health = new MailHealthIndicator(mailProperties(false, "smtp", ""), providerOf(null)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("enabled", false);
        assertThat(health.getDetails().get("reason").toString()).contains("mail.enabled=false");
    }

    @Test
    void isUpWhenTheSmtpServerAcceptsTheConnection() {
        JavaMailSenderImpl sender = mock(JavaMailSenderImpl.class);
        when(sender.getHost()).thenReturn("mailpit");
        when(sender.getPort()).thenReturn(1025);

        Health health = new MailHealthIndicator(mailProperties(true, "smtp", ""), providerOf(sender)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("enabled", true)
                .containsEntry("provider", "smtp")
                .containsEntry("host", "mailpit")
                .containsEntry("port", 1025);
    }

    @Test
    void isDegradedWhenTheSmtpServerDoesNotRespond() throws Exception {
        JavaMailSenderImpl sender = mock(JavaMailSenderImpl.class);
        when(sender.getHost()).thenReturn("mailpit");
        when(sender.getPort()).thenReturn(1025);
        doThrow(new MailAuthenticationException("credenciales rechazadas")).when(sender).testConnection();

        Health health = new MailHealthIndicator(mailProperties(true, "smtp", ""), providerOf(sender)).health();

        assertThat(health.getStatus()).isEqualTo(HealthStatuses.DEGRADED);
        assertThat(health.getDetails()).containsEntry("error", "MailAuthenticationException");
    }

    @Test
    void isDegradedWhenMailIsEnabledButNoSenderIsConfigured() {
        Health health = new MailHealthIndicator(mailProperties(true, "smtp", ""), providerOf(null)).health();

        assertThat(health.getStatus()).isEqualTo(HealthStatuses.DEGRADED);
        assertThat(health.getDetails().get("reason").toString()).contains("no hay JavaMailSender");
    }

    /** RS-014: ni usuario ni contrasena del SMTP aparecen en los detalles. */
    @Test
    void neverExposesSmtpCredentials() throws Exception {
        JavaMailSenderImpl sender = mock(JavaMailSenderImpl.class);
        when(sender.getHost()).thenReturn("mailpit");
        when(sender.getPort()).thenReturn(1025);
        doThrow(new MailAuthenticationException("login smtp-user/hunter2 rechazado")).when(sender).testConnection();

        Health health = new MailHealthIndicator(mailProperties(true, "smtp", ""), providerOf(sender)).health();

        assertThat(health.getDetails().values()).noneMatch(value -> value.toString().contains("hunter2"));
    }

    @Test
    void isUpWhenResendIsConfigured() {
        Health health = new MailHealthIndicator(mailProperties(true, "resend", "re_test_123"), providerOf(null)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("enabled", true)
                .containsEntry("provider", "resend")
                .containsEntry("baseUrl", "https://api.resend.com");
    }

    @Test
    void isDegradedWhenResendIsMissingApiKey() {
        Health health = new MailHealthIndicator(mailProperties(true, "resend", ""), providerOf(null)).health();

        assertThat(health.getStatus()).isEqualTo(HealthStatuses.DEGRADED);
        assertThat(health.getDetails().get("reason").toString()).contains("mail.provider=resend");
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSenderImpl> providerOf(JavaMailSenderImpl sender) {
        ObjectProvider<JavaMailSenderImpl> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    private MailProperties mailProperties(boolean enabled, String provider, String apiKey) {
        return new MailProperties(
                enabled,
                provider,
                "no-reply@acme.test",
                new MailProperties.Resend("https://api.resend.com", apiKey, java.time.Duration.ofSeconds(3), java.time.Duration.ofSeconds(10)));
    }
}
