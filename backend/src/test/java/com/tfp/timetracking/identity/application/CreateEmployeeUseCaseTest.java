package com.tfp.timetracking.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.identity.domain.EmailAlreadyInUseException;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateEmployeeUseCaseTest {

    @Test
    void rejectsDuplicateEmail() {
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        PasswordHasher passwordHasher = org.mockito.Mockito.mock(PasswordHasher.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        DomainEventPublisher publisher = org.mockito.Mockito.mock(DomainEventPublisher.class);
        AuditRecorder auditRecorder = org.mockito.Mockito.mock(AuditRecorder.class);
        CreateEmployeeUseCase useCase = new CreateEmployeeUseCase(
                userRepository, passwordHasher, tenantContext, () -> Instant.now(), UUID::randomUUID, publisher, auditRecorder);
        when(userRepository.existsByEmail(any())).thenReturn(true);
        when(tenantContext.currentTenantId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> useCase.create(new CreateEmployeeCommand(
                        "duplicate@example.com", "supersecretpwd", "Jane", "Doe", Set.of("EMPLOYEE"))))
                .isInstanceOf(EmailAlreadyInUseException.class);
    }
}
