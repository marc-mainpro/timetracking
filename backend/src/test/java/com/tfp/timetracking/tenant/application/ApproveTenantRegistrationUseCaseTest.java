package com.tfp.timetracking.tenant.application;

import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.CLOCK;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.ID_GENERATOR;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.pendingRegistration;
import static com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.verifiedRegistration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.identity.domain.EmailAlreadyInUseException;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.tenant.application.TenantRegistrationTestFixtures.StubTokenGenerator;
import com.tfp.timetracking.tenant.domain.IllegalTenantRegistrationTransitionException;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.TenantRegistrationRepository;
import com.tfp.timetracking.tenant.domain.TenantRegistrationStatus;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import com.tfp.timetracking.tenant.domain.TenantStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * T53-03: la aprobación crea tenant y propietario de forma transaccional, el
 * tenant nace {@code PENDING} (nunca {@code ACTIVE}) y la operación es
 * idempotente.
 */
class ApproveTenantRegistrationUseCaseTest {

    private final TenantRegistrationRepository registrationRepository = mock(TenantRegistrationRepository.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final StubTokenGenerator tokenGenerator = new StubTokenGenerator();

    private ApproveTenantRegistrationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ApproveTenantRegistrationUseCase(
                registrationRepository,
                tenantRepository,
                userRepository,
                domainEventPublisher,
                auditRecorder,
                CLOCK,
                ID_GENERATOR);
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.existsByEmail(any())).thenReturn(false);
    }

    @Test
    void createsAPendingTenantAndItsOwnerAdmin() {
        TenantRegistration registration = verifiedRegistration(tokenGenerator);
        when(registrationRepository.findById(registration.id())).thenReturn(Optional.of(registration));

        TenantRegistration result = useCase.approve(registration.id());

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().status()).isEqualTo(TenantStatus.PENDING);
        assertThat(tenantCaptor.getValue().name()).isEqualTo("Acme Corp");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().roles()).containsExactly(Role.TENANT_ADMIN);
        assertThat(userCaptor.getValue().passwordHash()).isEqualTo("hashed-password");
        assertThat(userCaptor.getValue().tenantId()).isEqualTo(tenantCaptor.getValue().id());

        assertThat(result.status()).isEqualTo(TenantRegistrationStatus.CONSUMED);
        assertThat(result.createdTenantId()).isEqualTo(tenantCaptor.getValue().id());
        verify(domainEventPublisher).publish(any());
        verify(auditRecorder).record(eq("TENANT_REGISTRATION_APPROVED"), eq("TenantRegistration"), any(), any());
    }

    @Test
    void approvingTwiceDoesNotCreateASecondTenant() {
        TenantRegistration registration = verifiedRegistration(tokenGenerator);
        when(registrationRepository.findById(registration.id())).thenReturn(Optional.of(registration));

        useCase.approve(registration.id());
        TenantRegistration second = useCase.approve(registration.id());

        verify(tenantRepository).save(any());
        verify(userRepository).save(any());
        assertThat(second.status()).isEqualTo(TenantRegistrationStatus.CONSUMED);
    }

    @Test
    void refusesToApproveARequestWhoseEmailIsNotVerifiedYet() {
        TenantRegistration registration = pendingRegistration(tokenGenerator);
        registration.pullDomainEvents();
        when(registrationRepository.findById(registration.id())).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> useCase.approve(registration.id()))
                .isInstanceOf(IllegalTenantRegistrationTransitionException.class);
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void failsWhenTheOwnerEmailWasTakenBetweenRequestAndApproval() {
        TenantRegistration registration = verifiedRegistration(tokenGenerator);
        when(registrationRepository.findById(registration.id())).thenReturn(Optional.of(registration));
        when(userRepository.existsByEmail(any())).thenReturn(true);

        assertThatThrownBy(() -> useCase.approve(registration.id()))
                .isInstanceOf(EmailAlreadyInUseException.class);
        verify(tenantRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void failsWhenTheRegistrationDoesNotExist() {
        UUID unknown = UUID.randomUUID();
        when(registrationRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.approve(unknown)).isInstanceOf(ResourceNotFoundException.class);
    }
}
