package com.tfp.timetracking.tenant.application;

import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.CLOCK;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.ID_GENERATOR;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.verifiedRegistration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.StubTokenGenerator;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.TenantRegistrationRepository;
import com.tfp.timetracking.tenant.domain.TenantRegistrationStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T53-03: rechazo con motivo obligatorio y auditoría. */
class RejectTenantRegistrationUseCaseTest {

    private final TenantRegistrationRepository registrationRepository = mock(TenantRegistrationRepository.class);
    private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final StubTokenGenerator tokenGenerator = new StubTokenGenerator();

    private RejectTenantRegistrationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RejectTenantRegistrationUseCase(
                registrationRepository, domainEventPublisher, auditRecorder, CLOCK, ID_GENERATOR);
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsAVerifiedRequestAndAuditsTheReason() {
        TenantRegistration registration = verifiedRegistration(tokenGenerator);
        when(registrationRepository.findById(registration.id())).thenReturn(Optional.of(registration));

        TenantRegistration result = useCase.reject(registration.id(), "Dominio desechable");

        assertThat(result.status()).isEqualTo(TenantRegistrationStatus.REJECTED);
        assertThat(result.decisionReason()).isEqualTo("Dominio desechable");
        verify(domainEventPublisher).publish(any());
        verify(auditRecorder).record(eq("TENANT_REGISTRATION_REJECTED"), eq("TenantRegistration"), any(), any());
    }

    @Test
    void requiresAReason() {
        TenantRegistration registration = verifiedRegistration(tokenGenerator);
        when(registrationRepository.findById(registration.id())).thenReturn(Optional.of(registration));

        assertThatIllegalArgumentException().isThrownBy(() -> useCase.reject(registration.id(), "   "));
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void failsWhenTheRegistrationDoesNotExist() {
        UUID unknown = UUID.randomUUID();
        when(registrationRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.reject(unknown, "motivo")).isInstanceOf(ResourceNotFoundException.class);
    }
}
