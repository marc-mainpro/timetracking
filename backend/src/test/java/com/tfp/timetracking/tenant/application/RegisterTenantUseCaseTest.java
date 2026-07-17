package com.tfp.timetracking.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.EmailAlreadyInUseException;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pruebas unitarias (Mockito) del caso de uso {@link RegisterTenantUseCase}
 * (ficha T203): exito, timezone invalida, email de admin duplicado dentro
 * del tenant.
 */
class RegisterTenantUseCaseTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final TenantRepository tenantRepository = org.mockito.Mockito.mock(TenantRepository.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final PasswordHasher passwordHasher = org.mockito.Mockito.mock(PasswordHasher.class);
    private final DomainEventPublisher domainEventPublisher = org.mockito.Mockito.mock(DomainEventPublisher.class);

    private final Clock clock = () -> FIXED_NOW;

    private RegisterTenantUseCase useCase;

    @BeforeEach
    void setUp() {
        Deque<UUID> ids = new ArrayDeque<>(List.of(TENANT_ID, TENANT_ID, ADMIN_ID, ADMIN_ID));
        IdGenerator idGenerator = () -> ids.isEmpty() ? UUID.randomUUID() : ids.poll();

        useCase = new RegisterTenantUseCase(
                tenantRepository, userRepository, passwordHasher, domainEventPublisher, clock, idGenerator);

        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordHasher.hash(any())).thenReturn("hashed-password");
    }

    private RegisterTenantCommand happyCommand() {
        return new RegisterTenantCommand(
                "Acme Corp", "Europe/Madrid", "admin@acme.test", "supersecretpwd", "Jane", "Doe");
    }

    @Test
    void registersTenantAndAdminAndPublishesEvents() {
        when(userRepository.existsByTenantIdAndEmail(any(), any())).thenReturn(false);

        RegisterTenantResult result = useCase.register(happyCommand());

        assertThat(result.tenantId()).isNotNull();
        assertThat(result.adminUserId()).isNotNull();

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().name()).isEqualTo("Acme Corp");
        assertThat(tenantCaptor.getValue().timezone()).isEqualTo("Europe/Madrid");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedAdmin = userCaptor.getValue();
        assertThat(savedAdmin.email()).isEqualTo(Email.of("admin@acme.test"));
        assertThat(savedAdmin.passwordHash()).isEqualTo("hashed-password");
        assertThat(savedAdmin.roles()).containsExactly(Role.TENANT_ADMIN);
        assertThat(savedAdmin.tenantId()).isEqualTo(tenantCaptor.getValue().id());

        ArgumentCaptor<List<Object>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(domainEventPublisher).publish(eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).hasSize(2);
    }

    @Test
    void rejectsInvalidTimezoneBeforeTouchingRepositories() {
        RegisterTenantCommand command =
                new RegisterTenantCommand("Acme Corp", "Not/AZone", "admin@acme.test", "supersecretpwd", "Jane", "Doe");

        assertThatIllegalArgumentException().isThrownBy(() -> useCase.register(command));

        verify(tenantRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void rejectsDuplicateAdminEmailWithinTenant() {
        when(userRepository.existsByTenantIdAndEmail(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> useCase.register(happyCommand()))
                .isInstanceOf(EmailAlreadyInUseException.class)
                .hasFieldOrPropertyWithValue("errorCode", "EMAIL_ALREADY_IN_USE");

        verify(tenantRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(domainEventPublisher, never()).publish(any());
    }
}
