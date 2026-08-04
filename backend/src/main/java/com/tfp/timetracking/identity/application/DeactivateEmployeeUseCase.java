package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.identity.domain.LastAdminException;
import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeactivateEmployeeUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantContext tenantContext;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditRecorder auditRecorder;

    public DeactivateEmployeeUseCase(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            TenantContext tenantContext,
            Clock clock,
            IdGenerator idGenerator,
            DomainEventPublisher domainEventPublisher,
            AuditRecorder auditRecorder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.domainEventPublisher = domainEventPublisher;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public User deactivate(UUID employeeId) {
        User user = userRepository.findById(tenantContext.currentTenantId(), employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado"));
        if (user.isActive() && user.hasRole(Role.TENANT_ADMIN)) {
            userRepository.lockActiveAdmins(tenantContext.currentTenantId());
            if (userRepository.countActiveAdminsExcludingUser(tenantContext.currentTenantId(), user.id()) == 0) {
                throw new LastAdminException();
            }
        }
        user.deactivate(clock, idGenerator);
        revokeRefreshTokens(user.id());
        User saved = userRepository.save(user);
        domainEventPublisher.publish(user.pullDomainEvents());
        auditRecorder.record("EMPLOYEE_DEACTIVATED", "User", saved.id(), java.util.Map.of());
        return saved;
    }

    private void revokeRefreshTokens(UUID userId) {
        for (RefreshToken refreshToken : refreshTokenRepository.findByUserId(userId)) {
            if (!refreshToken.isRevoked()) {
                refreshToken.revoke(clock.now());
                refreshTokenRepository.save(refreshToken);
            }
        }
    }
}
