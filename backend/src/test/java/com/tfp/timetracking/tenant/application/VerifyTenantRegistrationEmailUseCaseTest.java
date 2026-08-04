package com.tfp.timetracking.tenant.application;

import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.CLOCK;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.ID_GENERATOR;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.NOW;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.pendingRegistration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.StubTokenGenerator;
import com.tfp.timetracking.tenant.domain.InvalidVerificationTokenException;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.TenantRegistrationRepository;
import com.tfp.timetracking.tenant.domain.TenantRegistrationStatus;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** T53-05: token válido, inválido, caducado y ya usado. */
class VerifyTenantRegistrationEmailUseCaseTest {

    private final TenantRegistrationRepository registrationRepository = mock(TenantRegistrationRepository.class);
    private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
    private final RegistrationAuditTrail auditTrail = mock(RegistrationAuditTrail.class);
    private final StubTokenGenerator tokenGenerator = new StubTokenGenerator();

    private VerifyTenantRegistrationEmailUseCase useCase(Clock clock) {
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new VerifyTenantRegistrationEmailUseCase(
                registrationRepository, tokenGenerator, domainEventPublisher, auditTrail, clock, ID_GENERATOR);
    }

    @Test
    void verifiesAValidTokenAndMovesTheRequestToReview() {
        TenantRegistration registration = pendingRegistration(tokenGenerator);
        registration.pullDomainEvents();
        String token = tokenGenerator.lastValue();
        when(registrationRepository.findByVerificationTokenHash(tokenGenerator.hash(token)))
                .thenReturn(Optional.of(registration));

        useCase(CLOCK).verify(token);

        assertThat(registration.status()).isEqualTo(TenantRegistrationStatus.PENDING_REVIEW);
        verify(domainEventPublisher).publish(any());
        verify(auditTrail).record(eq("TENANT_REGISTRATION_EMAIL_VERIFIED"), any(), any());
    }

    @Test
    void rejectsAnUnknownToken() {
        when(registrationRepository.findByVerificationTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase(CLOCK).verify("inventado"))
                .isInstanceOf(InvalidVerificationTokenException.class);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void rejectsABlankTokenWithoutQueryingTheRepository() {
        assertThatThrownBy(() -> useCase(CLOCK).verify("  "))
                .isInstanceOf(InvalidVerificationTokenException.class);
        verify(registrationRepository, never()).findByVerificationTokenHash(any());
    }

    @Test
    void expiresTheRequestWhenTheTokenIsPastItsDeadline() {
        TenantRegistration registration = pendingRegistration(tokenGenerator);
        registration.pullDomainEvents();
        String token = tokenGenerator.lastValue();
        when(registrationRepository.findByVerificationTokenHash(tokenGenerator.hash(token)))
                .thenReturn(Optional.of(registration));
        Clock late = () -> NOW.plus(Duration.ofHours(25));

        assertThatThrownBy(() -> useCase(late).verify(token))
                .isInstanceOf(InvalidVerificationTokenException.class);

        assertThat(registration.status()).isEqualTo(TenantRegistrationStatus.EXPIRED);
        verify(registrationRepository).save(registration);
        verify(auditTrail).record(eq("TENANT_REGISTRATION_EXPIRED"), any(), any());
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void rejectsAnAlreadyVerifiedRequest() {
        TenantRegistration registration = pendingRegistration(tokenGenerator);
        String token = tokenGenerator.lastValue();
        registration.verifyEmail(token, tokenGenerator, CLOCK, ID_GENERATOR);
        registration.pullDomainEvents();
        // El adaptador ya no lo encontraría por hash (se borró), pero si un
        // consumidor concurrente lo hubiera leído antes, el agregado sigue
        // negándose.
        when(registrationRepository.findByVerificationTokenHash(tokenGenerator.hash(token)))
                .thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> useCase(CLOCK).verify(token))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }
}
