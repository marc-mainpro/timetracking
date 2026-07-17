package com.tfp.timetracking.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.identity.domain.LastAdminException;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeactivateEmployeeUseCaseTest {

    @Test
    void rejectsDeactivatingLastActiveAdmin() {
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        RefreshTokenRepository refreshTokenRepository = org.mockito.Mockito.mock(RefreshTokenRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        DeactivateEmployeeUseCase useCase = new DeactivateEmployeeUseCase(
                userRepository,
                refreshTokenRepository,
                tenantContext,
                () -> Instant.now(),
                UUID::randomUUID,
                org.mockito.Mockito.mock(DomainEventPublisher.class),
                org.mockito.Mockito.mock(AuditRecorder.class));
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User admin = User.reconstitute(
                userId,
                tenantId,
                "admin@example.com",
                "hash",
                "Admin",
                "One",
                UserStatus.ACTIVE,
                Set.of(Role.TENANT_ADMIN),
                Instant.now(),
                Instant.now());
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(userRepository.findById(tenantId, userId)).thenReturn(java.util.Optional.of(admin));
        when(userRepository.countActiveAdminsExcludingUser(tenantId, userId)).thenReturn(0L);

        assertThatThrownBy(() -> useCase.deactivate(userId)).isInstanceOf(LastAdminException.class);
    }
}
