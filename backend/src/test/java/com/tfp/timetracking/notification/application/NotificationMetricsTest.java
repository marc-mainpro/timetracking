package com.tfp.timetracking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class NotificationMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NotificationMetrics metrics = new NotificationMetrics(registry);

    @Test
    void countsSentFailedAndSkippedSeparately() {
        metrics.recordSent();
        metrics.recordSent();
        metrics.recordFailed();
        metrics.recordSkipped();

        assertThat(registry.get("notification.emails.sent").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("notification.emails.failed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("notification.emails.skipped").counter().count()).isEqualTo(1.0);
    }

    /** RS-014: los contadores no llevan destinatario ni asunto como tag. */
    @Test
    void exposesNoTagsThatCouldCarryPersonalData() {
        metrics.recordSent();

        assertThat(registry.get("notification.emails.sent").counter().getId().getTags()).isEmpty();
    }
}
