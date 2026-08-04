package com.tfp.timetracking.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationApproved;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationEmailVerified;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationRejected;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationRequested;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationVerificationRequested;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Reglas de negocio del agregado {@link TenantRegistration} (T53-01): estados,
 * transiciones válidas e inválidas, un solo uso del token, caducidad y límite
 * de reenvíos.
 */
class TenantRegistrationTest {

    private static final Instant NOW = Instant.parse("2026-02-01T09:00:00Z");
    private static final Duration TTL = Duration.ofHours(24);

    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private final Clock clock = now::get;
    private final IdGenerator idGenerator = UUID::randomUUID;
    private final StubTokenGenerator tokenGenerator = new StubTokenGenerator();

    /**
     * Generador determinista: el token n-ésimo es {@code token-n}. El hash es
     * el token codificado en Base64, de modo que —igual que un hash real— no
     * contiene el token como subcadena y el test de «solo se guarda el hash»
     * puede afirmar algo de verdad.
     */
    private static final class StubTokenGenerator implements VerificationTokenGenerator {
        private final AtomicInteger counter = new AtomicInteger();
        private String lastValue;

        @Override
        public VerificationToken generate() {
            lastValue = "token-" + counter.incrementAndGet();
            return new VerificationToken(lastValue, hash(lastValue));
        }

        @Override
        public String hash(String rawToken) {
            return java.util.Base64.getEncoder()
                    .encodeToString(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        String lastValue() {
            return lastValue;
        }
    }

    private TenantRegistration pending() {
        return TenantRegistration.request(
                "  Acme Corp  ",
                "Jane",
                "Doe",
                "  Owner@Acme.TEST ",
                "hashed-password",
                "Europe/Madrid",
                "PUBLIC_WEB",
                "ip-hash",
                TTL,
                clock,
                idGenerator,
                tokenGenerator);
    }

    @Test
    void requestNormalizesDataAndStartsPendingVerification() {
        TenantRegistration registration = pending();

        assertThat(registration.status()).isEqualTo(TenantRegistrationStatus.PENDING_EMAIL_VERIFICATION);
        assertThat(registration.companyName()).isEqualTo("Acme Corp");
        assertThat(registration.email()).isEqualTo("owner@acme.test");
        assertThat(registration.resendCount()).isZero();
        assertThat(registration.createdTenantId()).isNull();
        assertThat(registration.verificationTokenExpiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void requestStoresOnlyTheTokenHashNeverTheTokenItself() {
        TenantRegistration registration = pending();

        assertThat(registration.verificationTokenHash()).isEqualTo(tokenGenerator.hash("token-1"));
        assertThat(registration.verificationTokenHash()).doesNotContain(tokenGenerator.lastValue());
    }

    @Test
    void requestEmitsRequestedAndVerificationEvents() {
        TenantRegistration registration = pending();

        List<Object> events = registration.pullDomainEvents();

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(TenantRegistrationRequested.class);
        TenantRegistrationVerificationRequested verification =
                (TenantRegistrationVerificationRequested) events.get(1);
        assertThat(verification.verificationToken()).isEqualTo("token-1");
        assertThat(verification.resend()).isFalse();
        assertThat(registration.pullDomainEvents()).isEmpty();
    }

    @Test
    void requestRejectsBlankAndOversizedFields() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TenantRegistration.request(
                        " ", "Jane", "Doe", "o@a.test", "h", "Europe/Madrid", "PUBLIC_WEB", null, TTL, clock,
                        idGenerator, tokenGenerator))
                .withMessageContaining("organización");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TenantRegistration.request(
                        "Acme", " ", "Doe", "o@a.test", "h", "Europe/Madrid", "PUBLIC_WEB", null, TTL, clock,
                        idGenerator, tokenGenerator))
                .withMessageContaining("nombre del propietario");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TenantRegistration.request(
                        "Acme", "Jane", " ", "o@a.test", "h", "Europe/Madrid", "PUBLIC_WEB", null, TTL, clock,
                        idGenerator, tokenGenerator))
                .withMessageContaining("apellidos");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TenantRegistration.request(
                        "A".repeat(201), "Jane", "Doe", "o@a.test", "h", "Europe/Madrid", "PUBLIC_WEB", null, TTL,
                        clock, idGenerator, tokenGenerator))
                .withMessageContaining("máximo");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TenantRegistration.request(
                        "Acme", "Jane", "Doe", "o@a.test", " ", "Europe/Madrid", "PUBLIC_WEB", null, TTL, clock,
                        idGenerator, tokenGenerator))
                .withMessageContaining("contraseña");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TenantRegistration.request(
                        "Acme", "Jane", "Doe", "o@a.test", "h", "Europe/Madrid", " ", null, TTL, clock, idGenerator,
                        tokenGenerator))
                .withMessageContaining("fuente");
    }

    @Test
    void requestRejectsInvalidEmailAndTimezoneAndTtl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TenantRegistration.request(
                        "Acme", "Jane", "Doe", "no-arroba", "h", "Europe/Madrid", "PUBLIC_WEB", null, TTL, clock,
                        idGenerator, tokenGenerator))
                .withMessageContaining("Email");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TenantRegistration.request(
                        "Acme", "Jane", "Doe", " ", "h", "Europe/Madrid", "PUBLIC_WEB", null, TTL, clock, idGenerator,
                        tokenGenerator))
                .withMessageContaining("email");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TenantRegistration.request(
                        "Acme", "Jane", "Doe", "o@a.test", "h", "Not/AZone", "PUBLIC_WEB", null, TTL, clock,
                        idGenerator, tokenGenerator))
                .withMessageContaining("IANA");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TenantRegistration.request(
                        "Acme", "Jane", "Doe", "o@a.test", "h", " ", "PUBLIC_WEB", null, TTL, clock, idGenerator,
                        tokenGenerator))
                .withMessageContaining("zona horaria");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TenantRegistration.request(
                        "Acme", "Jane", "Doe", "o@a.test", "h", "Europe/Madrid", "PUBLIC_WEB", null, Duration.ZERO,
                        clock, idGenerator, tokenGenerator))
                .withMessageContaining("caducidad");
    }

    @Test
    void requestBlankIpHashIsStoredAsNull() {
        TenantRegistration registration = TenantRegistration.request(
                "Acme", "Jane", "Doe", "o@a.test", "h", "Europe/Madrid", "PUBLIC_WEB", "  ", TTL, clock, idGenerator,
                tokenGenerator);

        assertThat(registration.ipHash()).isNull();
    }

    @Test
    void verifyEmailMovesToPendingReviewAndBurnsTheToken() {
        TenantRegistration registration = pending();
        registration.pullDomainEvents();

        registration.verifyEmail("token-1", tokenGenerator, clock, idGenerator);

        assertThat(registration.status()).isEqualTo(TenantRegistrationStatus.PENDING_REVIEW);
        assertThat(registration.verificationTokenHash()).isNull();
        assertThat(registration.verifiedAt()).isEqualTo(NOW);
        assertThat(registration.pullDomainEvents()).singleElement().isInstanceOf(TenantRegistrationEmailVerified.class);
    }

    @Test
    void verifyEmailRejectsAWrongToken() {
        TenantRegistration registration = pending();

        assertThatThrownBy(() -> registration.verifyEmail("otro", tokenGenerator, clock, idGenerator))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_VERIFICATION_TOKEN");
        assertThat(registration.status()).isEqualTo(TenantRegistrationStatus.PENDING_EMAIL_VERIFICATION);
    }

    @Test
    void verifyEmailRejectsABlankToken() {
        TenantRegistration registration = pending();

        assertThatThrownBy(() -> registration.verifyEmail(" ", tokenGenerator, clock, idGenerator))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void verifyEmailRejectsAnExpiredToken() {
        TenantRegistration registration = pending();
        now.set(NOW.plus(TTL));

        assertThatThrownBy(() -> registration.verifyEmail("token-1", tokenGenerator, clock, idGenerator))
                .isInstanceOf(InvalidVerificationTokenException.class);
        assertThat(registration.isVerificationExpiredAt(now.get())).isTrue();
    }

    @Test
    void verifyEmailIsSingleUse() {
        TenantRegistration registration = pending();
        registration.verifyEmail("token-1", tokenGenerator, clock, idGenerator);

        assertThatThrownBy(() -> registration.verifyEmail("token-1", tokenGenerator, clock, idGenerator))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void resendIssuesANewTokenAndInvalidatesThePreviousOne() {
        TenantRegistration registration = pending();
        registration.pullDomainEvents();

        registration.resendVerification(3, TTL, clock, idGenerator, tokenGenerator);

        assertThat(registration.resendCount()).isEqualTo(1);
        assertThat(registration.verificationTokenHash()).isEqualTo(tokenGenerator.hash("token-2"));
        TenantRegistrationVerificationRequested event =
                (TenantRegistrationVerificationRequested) registration.pullDomainEvents().get(0);
        assertThat(event.resend()).isTrue();

        assertThatThrownBy(() -> registration.verifyEmail("token-1", tokenGenerator, clock, idGenerator))
                .isInstanceOf(InvalidVerificationTokenException.class);
        registration.verifyEmail("token-2", tokenGenerator, clock, idGenerator);
        assertThat(registration.status()).isEqualTo(TenantRegistrationStatus.PENDING_REVIEW);
    }

    @Test
    void resendStopsAtTheConfiguredLimit() {
        TenantRegistration registration = pending();
        registration.resendVerification(2, TTL, clock, idGenerator, tokenGenerator);
        registration.resendVerification(2, TTL, clock, idGenerator, tokenGenerator);

        assertThatThrownBy(() -> registration.resendVerification(2, TTL, clock, idGenerator, tokenGenerator))
                .isInstanceOf(VerificationResendLimitExceededException.class)
                .hasFieldOrPropertyWithValue("errorCode", "VERIFICATION_RESEND_LIMIT_EXCEEDED");
    }

    @Test
    void resendIsNotPossibleOnceVerified() {
        TenantRegistration registration = pending();
        registration.verifyEmail("token-1", tokenGenerator, clock, idGenerator);

        assertThatThrownBy(() -> registration.resendVerification(3, TTL, clock, idGenerator, tokenGenerator))
                .isInstanceOf(IllegalTenantRegistrationTransitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ILLEGAL_TENANT_REGISTRATION_TRANSITION");
    }

    @Test
    void expireOnlyAppliesWhilePendingVerification() {
        TenantRegistration registration = pending();
        registration.expire(clock);

        assertThat(registration.status()).isEqualTo(TenantRegistrationStatus.EXPIRED);
        assertThat(registration.verificationTokenHash()).isNull();
        assertThatThrownBy(() -> registration.expire(clock))
                .isInstanceOf(IllegalTenantRegistrationTransitionException.class);
    }

    @Test
    void approveRequiresAVerifiedRegistration() {
        TenantRegistration registration = pending();

        assertThatThrownBy(() -> registration.approve(clock))
                .isInstanceOf(IllegalTenantRegistrationTransitionException.class);

        registration.verifyEmail("token-1", tokenGenerator, clock, idGenerator);
        registration.approve(clock);

        assertThat(registration.status()).isEqualTo(TenantRegistrationStatus.APPROVED);
        assertThat(registration.decidedAt()).isEqualTo(NOW);
    }

    @Test
    void markConsumedRecordsTheCreatedTenantAndIsTerminal() {
        TenantRegistration registration = pending();
        registration.verifyEmail("token-1", tokenGenerator, clock, idGenerator);
        registration.approve(clock);
        registration.pullDomainEvents();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        registration.markConsumed(tenantId, ownerId, clock, idGenerator);

        assertThat(registration.status()).isEqualTo(TenantRegistrationStatus.CONSUMED);
        assertThat(registration.createdTenantId()).isEqualTo(tenantId);
        TenantRegistrationApproved event = (TenantRegistrationApproved) registration.pullDomainEvents().get(0);
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.ownerUserId()).isEqualTo(ownerId);

        assertThatThrownBy(() -> registration.markConsumed(tenantId, ownerId, clock, idGenerator))
                .isInstanceOf(IllegalTenantRegistrationTransitionException.class);
    }

    @Test
    void markConsumedRequiresIds() {
        TenantRegistration registration = pending();
        registration.verifyEmail("token-1", tokenGenerator, clock, idGenerator);
        registration.approve(clock);

        assertThatThrownBy(() -> registration.markConsumed(null, UUID.randomUUID(), clock, idGenerator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registration.markConsumed(UUID.randomUUID(), null, clock, idGenerator))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectRequiresAReasonAndOnlyAppliesToVerifiedRegistrations() {
        TenantRegistration pendingVerification = pending();
        assertThatThrownBy(() -> pendingVerification.reject("spam", clock, idGenerator))
                .isInstanceOf(IllegalTenantRegistrationTransitionException.class);

        TenantRegistration registration = pending();
        registration.verifyEmail(tokenGenerator.lastValue(), tokenGenerator, clock, idGenerator);
        registration.pullDomainEvents();

        assertThatIllegalArgumentException().isThrownBy(() -> registration.reject("  ", clock, idGenerator));

        registration.reject("  Dominio desechable  ", clock, idGenerator);

        assertThat(registration.status()).isEqualTo(TenantRegistrationStatus.REJECTED);
        assertThat(registration.decisionReason()).isEqualTo("Dominio desechable");
        TenantRegistrationRejected event = (TenantRegistrationRejected) registration.pullDomainEvents().get(0);
        assertThat(event.reason()).isEqualTo("Dominio desechable");
    }

    @Test
    void approvedRegistrationCannotBeRejectedAfterwards() {
        TenantRegistration registration = pending();
        registration.verifyEmail("token-1", tokenGenerator, clock, idGenerator);
        registration.approve(clock);

        assertThatThrownBy(() -> registration.reject("tarde", clock, idGenerator))
                .isInstanceOf(IllegalTenantRegistrationTransitionException.class);
    }

    @Test
    void reconstituteRestoresStateWithoutEvents() {
        TenantRegistration registration = TenantRegistration.reconstitute(
                UUID.randomUUID(),
                "Acme",
                "Jane",
                "Doe",
                "owner@acme.test",
                "hash",
                "Europe/Madrid",
                TenantRegistrationStatus.PENDING_REVIEW,
                null,
                null,
                NOW,
                1,
                "PUBLIC_WEB",
                "ip",
                null,
                null,
                NOW,
                NOW,
                NOW,
                null);

        assertThat(registration.pullDomainEvents()).isEmpty();
        assertThat(registration.status()).isEqualTo(TenantRegistrationStatus.PENDING_REVIEW);
        assertThat(registration.resendCount()).isEqualTo(1);
        assertThat(registration.ownerPasswordHash()).isEqualTo("hash");
        assertThat(registration.ownerLastName()).isEqualTo("Doe");
        assertThat(registration.verificationSentAt()).isEqualTo(NOW);
        assertThat(registration.updatedAt()).isEqualTo(NOW);
        assertThat(registration.timezone()).isEqualTo("Europe/Madrid");
        assertThat(registration.decidedAt()).isNull();
    }

    @Test
    void reconstituteRequiresIdentityAndTimestamps() {
        assertThatThrownBy(() -> TenantRegistration.reconstitute(
                        null, "A", "B", "C", "o@a.test", "h", "UTC", TenantRegistrationStatus.EXPIRED, null, null,
                        null, 0, "PUBLIC_WEB", null, null, null, NOW, NOW, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void openStatusesAreTheOnesThatBlockANewRequest() {
        assertThat(TenantRegistrationStatus.PENDING_EMAIL_VERIFICATION.isOpen()).isTrue();
        assertThat(TenantRegistrationStatus.PENDING_REVIEW.isOpen()).isTrue();
        assertThat(TenantRegistrationStatus.APPROVED.isOpen()).isTrue();
        assertThat(TenantRegistrationStatus.REJECTED.isOpen()).isFalse();
        assertThat(TenantRegistrationStatus.EXPIRED.isOpen()).isFalse();
        assertThat(TenantRegistrationStatus.CONSUMED.isOpen()).isFalse();
    }

    @Test
    void verificationTokenRejectsEmptyParts() {
        assertThatIllegalArgumentException().isThrownBy(() -> new VerificationToken(" ", "hash"));
        assertThatIllegalArgumentException().isThrownBy(() -> new VerificationToken("token", " "));
    }
}
