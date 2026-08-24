package com.tfp.timetracking.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MailPropertiesTest {

    @Test
    void normalizesProviderBeforeUsingIt() {
        MailProperties properties =
                new MailProperties(
                        true,
                        " ReSeNd ",
                        "no-reply@acme.test",
                        new MailProperties.Resend(null, null, null, null));

        assertThat(properties.provider()).isEqualTo("resend");
        assertThat(properties.usesResend()).isTrue();
        assertThat(properties.resend().baseUrl()).isEqualTo("https://api.resend.com");
        assertThat(properties.resend().connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.resend().readTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void rejectsUnsupportedProviderValues() {
        assertThatThrownBy(
                        () -> new MailProperties(
                                true,
                                "resned",
                                "no-reply@acme.test",
                                new MailProperties.Resend("https://api.resend.com", "re_test_123", Duration.ofSeconds(1), Duration.ofSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mail.provider");
    }
}
