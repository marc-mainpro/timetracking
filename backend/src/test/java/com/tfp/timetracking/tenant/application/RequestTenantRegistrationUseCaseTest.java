package com.tfp.timetracking.tenant.application;

import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.CLOCK;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.ID_GENERATOR;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.pendingRegistration;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.properties;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.verifiedRegistration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.StubTokenGenerator;
import com.tfp.timetracking.tenant.domain.IpHasher;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.TenantRegistrationRepository;
import com.tfp.timetracking.tenant.domain.TenantRegistrationStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * T53-03/T53-05: el alta pública crea una solicitud y nunca un tenant, y es
 * <b>indistinguible</b> desde fuera tanto si el correo ya existe como si la
 * solicitud se descarta por abuso (RF-REG-005).
 */
class RequestTenantRegistrationUseCaseTest {

    private final TenantRegistrationRepository registrationRepository = mock(TenantRegistrationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final IpHasher ipHasher = mock(IpHasher.class);
    private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
    private final RegistrationAuditTrail auditTrail = mock(RegistrationAuditTrail.class);
    private final StubTokenGenerator tokenGenerator = new StubTokenGenerator();

    private RequestTenantRegistrationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RequestTenantRegistrationUseCase(
                registrationRepository,
                userRepository,
                passwordHasher,
                tokenGenerator,
                ipHasher,
                domainEventPublisher,
                auditTrail,
                properties(),
                CLOCK,
                ID_GENERATOR);
        when(passwordHasher.hash(anyString())).thenReturn("hashed-password");
        when(ipHasher.hash(any())).thenReturn("ip-hash");
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationRepository.findOpenByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.existsByEmail(any())).thenReturn(false);
    }

    private RequestTenantRegistrationCommand command() {
        return new RequestTenantRegistrationCommand(
                "Acme Corp",
                "Jane",
                "Doe",
                "Owner@Acme.test",
                "supersecretpwd",
                "Europe/Madrid",
                "PUBLIC_WEB",
                "203.0.113.10");
    }

    @Test
    void createsAPendingRegistrationAndPublishesItsEvents() {
        useCase.request(command());

        ArgumentCaptor<TenantRegistration> captor = ArgumentCaptor.forClass(TenantRegistration.class);
        verify(registrationRepository).save(captor.capture());
        TenantRegistration saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(TenantRegistrationStatus.PENDING_EMAIL_VERIFICATION);
        assertThat(saved.email()).isEqualTo("owner@acme.test");
        assertThat(saved.ownerPasswordHash()).isEqualTo("hashed-password");
        assertThat(saved.ipHash()).isEqualTo("ip-hash");
        verify(domainEventPublisher).publish(any());
        verify(auditTrail).record(eq("TENANT_REGISTRATION_REQUESTED"), any(), any());
    }

    @Test
    void neverPersistsTheRawPasswordOrTheRawIp() {
        useCase.request(command());

        ArgumentCaptor<TenantRegistration> captor = ArgumentCaptor.forClass(TenantRegistration.class);
        verify(registrationRepository).save(captor.capture());
        assertThat(captor.getValue().ownerPasswordHash()).isNotEqualTo("supersecretpwd");
        assertThat(captor.getValue().ipHash()).isNotEqualTo("203.0.113.10");
    }

    @Test
    void doesNothingAndSaysNothingWhenTheEmailAlreadyHasAnAccount() {
        when(userRepository.existsByEmail(any())).thenReturn(true);

        useCase.request(command());

        verify(registrationRepository, never()).save(any());
        verify(domainEventPublisher, never()).publish(any());
        verify(auditTrail, never()).record(any(), any(), any());
    }

    @Test
    void hashesThePasswordEvenWhenTheEmailAlreadyExists() {
        // Mismo trabajo caro en ambos caminos: el tiempo de respuesta no delata
        // si el correo estaba registrado (RF-REG-005).
        when(userRepository.existsByEmail(any())).thenReturn(true);

        useCase.request(command());

        verify(passwordHasher).hash("supersecretpwd");
    }

    @Test
    void discardsTheRequestWhenTheIpExceededItsQuota() {
        when(registrationRepository.countByIpHashSince(eq("ip-hash"), any(Instant.class)))
                .thenReturn(5L);

        useCase.request(command());

        verify(registrationRepository, never()).save(any());
        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void discardsTheRequestWhenTheEmailExceededItsQuota() {
        when(registrationRepository.countByEmailSince(eq("owner@acme.test"), any(Instant.class)))
                .thenReturn(3L);

        useCase.request(command());

        verify(registrationRepository, never()).save(any());
    }

    @Test
    void resendsInsteadOfCreatingADuplicateWhenARequestIsAlreadyPending() {
        TenantRegistration open = pendingRegistration(tokenGenerator);
        open.pullDomainEvents();
        when(registrationRepository.findOpenByEmail("owner@acme.test")).thenReturn(Optional.of(open));

        useCase.request(command());

        assertThat(open.resendCount()).isEqualTo(1);
        verify(registrationRepository).save(open);
        verify(auditTrail).record(eq("TENANT_REGISTRATION_VERIFICATION_RESENT"), any(), any());
    }

    @Test
    void staysSilentWhenTheOpenRequestCannotBeResent() {
        TenantRegistration verified = verifiedRegistration(tokenGenerator);
        when(registrationRepository.findOpenByEmail("owner@acme.test")).thenReturn(Optional.of(verified));

        useCase.request(command());

        verify(registrationRepository, never()).save(any());
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void rejectsAMalformedEmailBeforeTouchingAnyRepository() {
        RequestTenantRegistrationCommand malformed = new RequestTenantRegistrationCommand(
                "Acme", "Jane", "Doe", "no-arroba", "supersecretpwd", "Europe/Madrid", "PUBLIC_WEB", null);

        assertThatIllegalArgumentException().isThrownBy(() -> useCase.request(malformed));

        verify(registrationRepository, never()).save(any());
    }
}
