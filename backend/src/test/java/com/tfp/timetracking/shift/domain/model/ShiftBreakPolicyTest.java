package com.tfp.timetracking.shift.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ShiftBreakPolicyTest {

    @Test
    void treatsAMissingBreakAsNoBreak() {
        assertThat(new ShiftBreakPolicy(null).plannedBreakDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    void keepsTheConfiguredBreak() {
        assertThat(new ShiftBreakPolicy(Duration.ofMinutes(45)).plannedBreakDuration())
                .isEqualTo(Duration.ofMinutes(45));
    }

    @Test
    void rejectsANegativeBreak() {
        assertThatThrownBy(() -> new ShiftBreakPolicy(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativa");
    }
}
