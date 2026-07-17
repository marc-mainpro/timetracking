package com.tfp.timetracking.corrections.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProposedChangesTest {

    private static final Instant START = Instant.parse("2026-01-15T09:00:00Z");
    private static final Instant END = Instant.parse("2026-01-15T18:00:00Z");

    @Test
    void createsDefensiveCopyOfBreaks() {
        ProposedChanges changes = new ProposedChanges(
                START,
                END,
                List.of(new ProposedChanges.ProposedBreak(Instant.parse("2026-01-15T12:00:00Z"), Instant.parse("2026-01-15T12:30:00Z"))));

        assertThat(changes.breaks()).hasSize(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> changes.breaks().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ProposedChanges(END, START, List.of()));
    }

    @Test
    void rejectsBreakBeforeWorkdayStart() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ProposedChanges(
                START,
                END,
                List.of(new ProposedChanges.ProposedBreak(Instant.parse("2026-01-15T08:00:00Z"), Instant.parse("2026-01-15T08:30:00Z")))));
    }

    @Test
    void rejectsBreakAfterWorkdayEnd() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ProposedChanges(
                START,
                END,
                List.of(new ProposedChanges.ProposedBreak(Instant.parse("2026-01-15T17:30:00Z"), Instant.parse("2026-01-15T18:30:00Z")))));
    }
}
