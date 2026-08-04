package com.tfp.timetracking.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.identity.domain.AccountLockout;
import com.tfp.timetracking.identity.domain.AccountLockoutRepository;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.shared.domain.Clock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** Orquestacion del bloqueo temporal: persistencia, auditoria y metricas (RS-008). */
class AccountLockoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final AccountLockoutRepository repository = Mockito.mock(AccountLockoutRepository.class);
    private final AuditRecorder auditRecorder = Mockito.mock(AuditRecorder.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AuthenticationMetrics metrics = new AuthenticationMetrics(meterRegistry);
    private final Clock clock = () -> NOW;

    private final AccountLockoutService service = new AccountLockoutService(
            repository, auditRecorder, metrics, clock, 2, Duration.ofMinutes(15), Duration.ofMinutes(30));

    @Test
    void exposesTheConfiguredPolicy() {
        assertThat(service.policy().threshold()).isEqualTo(2);
        assertThat(service.policy().lockDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(service.policy().failureWindow()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void rejectsANonSensiblePolicy() {
        assertThatThrownBy(() -> new AccountLockoutService(
                        repository, auditRecorder, metrics, clock, 0, Duration.ofMinutes(15), Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountLockoutService(
                        repository, auditRecorder, metrics, clock, 3, Duration.ZERO, Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsTheLockoutRecordOnTheFirstFailure() {
        when(repository.findByUserId(TENANT_ID, USER_ID)).thenReturn(Optional.empty());

        boolean locked = service.registerFailedAttempt(user());

        assertThat(locked).isFalse();
        ArgumentCaptor<AccountLockout> saved = ArgumentCaptor.forClass(AccountLockout.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().failedAttempts()).isEqualTo(1);
        assertThat(saved.getValue().lockedUntil()).isNull();
        verify(auditRecorder)
                .record(eq(TENANT_ID), eq(USER_ID), eq(AccountLockoutService.AUDIT_LOGIN_FAILED), eq("User"), eq(USER_ID), any());
        verify(auditRecorder, never())
                .record(any(), any(), eq(AccountLockoutService.AUDIT_ACCOUNT_LOCKED), any(), any(), any());
    }

    @Test
    void locksAuditsAndCountsWhenTheThresholdIsReached() {
        AccountLockout existing =
                AccountLockout.reconstitute(USER_ID, TENANT_ID, 1, NOW.minusSeconds(60), null, NOW, NOW);
        when(repository.findByUserId(TENANT_ID, USER_ID)).thenReturn(Optional.of(existing));

        boolean locked = service.registerFailedAttempt(user());

        assertThat(locked).isTrue();
        assertThat(existing.lockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditRecorder)
                .record(
                        eq(TENANT_ID),
                        eq(USER_ID),
                        eq(AccountLockoutService.AUDIT_ACCOUNT_LOCKED),
                        eq("User"),
                        eq(USER_ID),
                        metadata.capture());
        assertThat(metadata.getValue()).containsEntry("threshold", 2);
        assertThat(metadata.getValue()).containsKey("lockedUntil");

        assertThat(meterRegistry.find("auth.accounts.locked").counter().count()).isEqualTo(1.0);
    }

    @Test
    void isLockedReflectsThePersistedState() {
        when(repository.findByUserId(TENANT_ID, USER_ID))
                .thenReturn(Optional.of(AccountLockout.reconstitute(
                        USER_ID, TENANT_ID, 0, NOW.minusSeconds(10), NOW.plusSeconds(60), NOW, NOW)));

        assertThat(service.isLocked(user())).isTrue();
    }

    @Test
    void isLockedIsFalseWhenTheLockHasAlreadyExpired() {
        when(repository.findByUserId(TENANT_ID, USER_ID))
                .thenReturn(Optional.of(AccountLockout.reconstitute(
                        USER_ID, TENANT_ID, 0, NOW.minusSeconds(600), NOW.minusSeconds(1), NOW, NOW)));

        assertThat(service.isLocked(user())).isFalse();
    }

    @Test
    void isLockedIsFalseWhenThereIsNoRecordAtAll() {
        when(repository.findByUserId(TENANT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThat(service.isLocked(user())).isFalse();
    }

    @Test
    void successResetsAnExistingRecord() {
        AccountLockout existing = AccountLockout.reconstitute(
                USER_ID, TENANT_ID, 1, NOW.minusSeconds(60), NOW.minusSeconds(1), NOW, NOW);
        when(repository.findByUserId(TENANT_ID, USER_ID)).thenReturn(Optional.of(existing));

        service.registerSuccessfulAttempt(user());

        verify(repository).save(existing);
        assertThat(existing.failedAttempts()).isZero();
        assertThat(existing.lockedUntil()).isNull();
    }

    @Test
    void successDoesNotWriteWhenThereIsNothingToReset() {
        when(repository.findByUserId(TENANT_ID, USER_ID)).thenReturn(Optional.empty());
        service.registerSuccessfulAttempt(user());
        verify(repository, never()).save(any());

        when(repository.findByUserId(TENANT_ID, USER_ID))
                .thenReturn(Optional.of(AccountLockout.start(TENANT_ID, USER_ID, clock)));
        service.registerSuccessfulAttempt(user());
        verify(repository, never()).save(any());
    }

    @Test
    void blockedAttemptIsAuditedButDoesNotExtendTheLock() {
        service.registerBlockedAttempt(user());

        verify(auditRecorder)
                .record(
                        eq(TENANT_ID),
                        eq(USER_ID),
                        eq(AccountLockoutService.AUDIT_LOGIN_BLOCKED),
                        eq("User"),
                        eq(USER_ID),
                        any());
        verify(repository, never()).save(any());
    }

    private User user() {
        return User.reconstitute(
                USER_ID,
                TENANT_ID,
                "jane@example.com",
                "hash",
                "Jane",
                "Doe",
                UserStatus.ACTIVE,
                Set.of(Role.EMPLOYEE),
                NOW,
                NOW);
    }
}
