package com.tfp.timetracking.identity.infrastructure.security;

import com.tfp.timetracking.identity.domain.InvalidCredentialsException;
import com.tfp.timetracking.identity.domain.Session;
import com.tfp.timetracking.identity.domain.SessionInactiveException;
import com.tfp.timetracking.identity.domain.SessionRepository;
import com.tfp.timetracking.identity.domain.TenantAccessRepository;
import com.tfp.timetracking.identity.domain.TenantInactiveException;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserInactiveException;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.application.AuthenticatedPrincipalStateChecker;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdentityAuthenticatedPrincipalStateChecker implements AuthenticatedPrincipalStateChecker {

    private final UserRepository userRepository;
    private final TenantAccessRepository tenantAccessRepository;
    private final SessionRepository sessionRepository;

    public IdentityAuthenticatedPrincipalStateChecker(
            UserRepository userRepository,
            TenantAccessRepository tenantAccessRepository,
            SessionRepository sessionRepository) {
        this.userRepository = userRepository;
        this.tenantAccessRepository = tenantAccessRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void ensureActivePrincipal(UUID tenantId, UUID userId, UUID sessionId) {
        User user = userRepository.findById(tenantId, userId).orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive()) {
            throw new UserInactiveException();
        }
        if (!tenantAccessRepository.isActive(tenantId)) {
            throw new TenantInactiveException();
        }
        if (sessionId != null) {
            Session session = sessionRepository.findById(tenantId, userId, sessionId).orElseThrow(SessionInactiveException::new);
            if (!session.isActiveAt(java.time.Instant.now())) {
                throw new SessionInactiveException();
            }
        }
    }
}
