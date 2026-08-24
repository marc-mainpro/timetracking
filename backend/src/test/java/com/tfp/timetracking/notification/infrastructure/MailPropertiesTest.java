package com.tfp.timetracking.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MailPropertiesTest {

    @Test
    void normalizesProviderBeforeUsingIt() {
        MailProperties properties =
                new MailProperties(true, " ReSeNd ", "no-reply@acme.test", new MailProperties.Resend(null, null));

        assertThat(properties.provider()).isEqualTo("resend");
        assertThat(properties.usesResend()).isTrue();
        assertThat(properties.resend().baseUrl()).isEqualTo("https://api.resend.com");
    }

    @Test
    void rejectsUnsupportedProviderValues() {
        assertThatThrownBy(
                        () -> new MailProperties(
                                true,
                                "resned",
                                "no-reply@acme.test",
                                new MailProperties.Resend("https://api.resend.com", "re_test_123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mail.provider");
    }
}
