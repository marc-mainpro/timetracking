package com.tfp.timetracking.tenant.application;

import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.CLOCK;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.ID_GENERATOR;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.pendingRegistration;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.properties;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.verifiedRegistration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.StubTokenGenerator;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.TenantRegistrationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T53-05: el reenvío es anti-enumeración (RF-REG-005): correo desconocido,
 * solicitud ya verificada y límite agotado terminan sin excepción y sin efecto
 * observable.
 */
class ResendTenantRegistrationVerificationUseCaseTest {

    private final TenantRegistrationRepository registrationRepository = mock(TenantRegistrationRepository.class);
    private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
    private final RegistrationAuditTrail auditTrail = mock(RegistrationAuditTrail.class);
    private final StubTokenGenerator tokenGenerator = new StubTokenGenerator();

    private ResendTenantRegistrationVerificationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ResendTenantRegistrationVerificationUseCase(
                registrationRepository,
                tokenGenerator,
                domainEventPublisher,
                auditTrail,
                properties(),
                CLOCK,
                ID_GENERATOR);
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void resendsWhenARequestIsStillPendingVerification() {
        TenantRegistration registration = pendingRegistration(tokenGenerator);
        registration.pullDomainEvents();
        when(registrationRepository.findOpenByEmail("owner@acme.test")).thenReturn(Optional.of(registration));

        useCase.resend("Owner@Acme.test");

        assertThat(registration.resendCount()).isEqualTo(1);
        verify(registrationRepository).save(registration);
        verify(domainEventPublisher).publish(any());
        verify(auditTrail).record(eq("TENANT_REGISTRATION_VERIFICATION_RESENT"), any(), any());
    }

    @Test
    void staysSilentForAnUnknownEmail() {
        when(registrationRepository.findOpenByEmail(any())).thenReturn(Optional.empty());

        useCase.resend("desconocido@acme.test");

        verify(registrationRepository, never()).save(any());
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void staysSilentForAMalformedEmail() {
        useCase.resend("no-arroba");

        verify(registrationRepository, never()).findOpenByEmail(any());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void staysSilentWhenTheRequestIsAlreadyVerified() {
        when(registrationRepository.findOpenByEmail(any()))
                .thenReturn(Optional.of(verifiedRegistration(tokenGenerator)));

        useCase.resend("owner@acme.test");

        verify(registrationRepository, never()).save(any());
    }

    @Test
    void staysSilentWhenTheResendLimitIsExhausted() {
        TenantRegistration registration = pendingRegistration(tokenGenerator);
        for (int i = 0; i < 3; i++) {
            registration.resendVerification(3, java.time.Duration.ofHours(24), CLOCK, ID_GENERATOR, tokenGenerator);
        }
        registration.pullDomainEvents();
        when(registrationRepository.findOpenByEmail(any())).thenReturn(Optional.of(registration));

        useCase.resend("owner@acme.test");

        assertThat(registration.resendCount()).isEqualTo(3);
        verify(registrationRepository, never()).save(any());
        verify(domainEventPublisher, never()).publish(any());
    }
}
