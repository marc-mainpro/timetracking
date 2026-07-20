package com.tfp.timetracking.reporting.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class ReportDateRangeTest {

    @Test
    void acceptsAValidRange() {
        assertThatCode(() -> new ReportDateRange(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-31T00:00:00Z")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsToBeforeFrom() {
        assertThatThrownBy(
                        () -> new ReportDateRange(Instant.parse("2026-01-31T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsExactlyThreeHundredSixtySixDays() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = from.plus(366, ChronoUnit.DAYS);
        assertThatCode(() -> new ReportDateRange(from, to)).doesNotThrowAnyException();
    }

    @Test
    void rejectsRangesLongerThanThreeHundredSixtySixDays() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = from.plus(367, ChronoUnit.DAYS);
        assertThatThrownBy(() -> new ReportDateRange(from, to)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullBounds() {
        assertThatThrownBy(() -> new ReportDateRange(null, Instant.now())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReportDateRange(Instant.now(), null)).isInstanceOf(NullPointerException.class);
    }
}
