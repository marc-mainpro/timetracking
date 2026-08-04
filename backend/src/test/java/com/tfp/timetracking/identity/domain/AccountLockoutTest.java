package com.tfp.timetracking.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.shared.domain.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Reglas de bloqueo temporal de cuenta (RF-USR-008, RS-008, diseño §8.5). */
class AccountLockoutTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant START = Instant.parse("2026-03-01T09:00:00Z");
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final Duration WINDOW = Duration.ofMinutes(30);

    /** Reloj controlable: el dominio nunca llama a {@code Instant.now()}. */
    private static final class MutableClock implements Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant now() {
            return now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }
    }

    @Test
    void startsUnlockedAndWithoutFailures() {
        MutableClock clock = new MutableClock(START);

        AccountLockout lockout = AccountLockout.start(TENANT_ID, USER_ID, clock);

        assertThat(lockout.failedAttempts()).isZero();
        assertThat(lockout.lastFailedAttemptAt()).isNull();
        assertThat(lockout.lockedUntil()).isNull();
        assertThat(lockout.isLocked(clock)).isFalse();
        assertThat(lockout.isPristine()).isTrue();
    }

    @Test
    void locksTheAccountExactlyAtTheConfiguredThreshold() {
        MutableClock clock = new MutableClock(START);
        AccountLockout lockout = AccountLockout.start(TENANT_ID, USER_ID, clock);

        assertThat(lockout.registerFailure(3, LOCK, WINDOW, clock)).isFalse();
        assertThat(lockout.registerFailure(3, LOCK, WINDOW, clock)).isFalse();
        assertThat(lockout.isLocked(clock)).isFalse();

        assertThat(lockout.registerFailure(3, LOCK, WINDOW, clock)).isTrue();

        assertThat(lockout.isLocked(clock)).isTrue();
        assertThat(lockout.lockedUntil()).isEqualTo(START.plus(LOCK));
        assertThat(lockout.lastFailedAttemptAt()).isEqualTo(START);
    }

    @Test
    void unlocksByItselfWhenTheLockDurationElapses() {
        MutableClock clock = new MutableClock(START);
        AccountLockout lockout = lockedAccount(clock);

        clock.advance(LOCK.minusSeconds(1));
        assertThat(lockout.isLocked(clock)).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(lockout.isLocked(clock)).isFalse();
    }

    @Test
    void grantsAFullBudgetOfAttemptsAgainAfterTheLockExpires() {
        MutableClock clock = new MutableClock(START);
        AccountLockout lockout = lockedAccount(clock);
        clock.advance(LOCK);

        assertThat(lockout.registerFailure(2, LOCK, WINDOW, clock)).isFalse();
        assertThat(lockout.isLocked(clock)).isFalse();

        assertThat(lockout.registerFailure(2, LOCK, WINDOW, clock)).isTrue();
    }

    @Test
    void forgetsFailuresOlderThanTheFailureWindow() {
        MutableClock clock = new MutableClock(START);
        AccountLockout lockout = AccountLockout.start(TENANT_ID, USER_ID, clock);

        lockout.registerFailure(3, LOCK, WINDOW, clock);
        lockout.registerFailure(3, LOCK, WINDOW, clock);
        assertThat(lockout.failedAttempts()).isEqualTo(2);

        clock.advance(WINDOW.plusSeconds(1));

        assertThat(lockout.registerFailure(3, LOCK, WINDOW, clock)).isFalse();
        assertThat(lockout.failedAttempts()).isEqualTo(1);
    }

    @Test
    void keepsCountingFailuresThatFallInsideTheWindow() {
        MutableClock clock = new MutableClock(START);
        AccountLockout lockout = AccountLockout.start(TENANT_ID, USER_ID, clock);

        lockout.registerFailure(3, LOCK, WINDOW, clock);
        clock.advance(WINDOW.minusSeconds(1));
        lockout.registerFailure(3, LOCK, WINDOW, clock);

        assertThat(lockout.failedAttempts()).isEqualTo(2);
    }

    @Test
    void successfulAuthenticationResetsCounterAndLock() {
        MutableClock clock = new MutableClock(START);
        AccountLockout lockout = lockedAccount(clock);
        clock.advance(LOCK);

        lockout.registerSuccess(clock);

        assertThat(lockout.failedAttempts()).isZero();
        assertThat(lockout.lastFailedAttemptAt()).isNull();
        assertThat(lockout.lockedUntil()).isNull();
        assertThat(lockout.isLocked(clock)).isFalse();
        assertThat(lockout.isPristine()).isTrue();
    }

    @Test
    void rejectsAThresholdBelowOne() {
        MutableClock clock = new MutableClock(START);
        AccountLockout lockout = AccountLockout.start(TENANT_ID, USER_ID, clock);

        assertThatThrownBy(() -> lockout.registerFailure(0, LOCK, WINDOW, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstituteRejectsANegativeCounter() {
        assertThatThrownBy(() -> AccountLockout.reconstitute(USER_ID, TENANT_ID, -1, null, null, START, START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitutePreservesPersistedState() {
        AccountLockout lockout =
                AccountLockout.reconstitute(USER_ID, TENANT_ID, 2, START, START.plus(LOCK), START, START);

        assertThat(lockout.userId()).isEqualTo(USER_ID);
        assertThat(lockout.tenantId()).isEqualTo(TENANT_ID);
        assertThat(lockout.failedAttempts()).isEqualTo(2);
        assertThat(lockout.isLocked(new MutableClock(START))).isTrue();
        assertThat(lockout.isPristine()).isFalse();
    }

    private AccountLockout lockedAccount(MutableClock clock) {
        AccountLockout lockout = AccountLockout.start(TENANT_ID, USER_ID, clock);
        lockout.registerFailure(2, LOCK, WINDOW, clock);
        lockout.registerFailure(2, LOCK, WINDOW, clock);
        return lockout;
    }
}
