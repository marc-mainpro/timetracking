package com.tfp.timetracking.identity.infrastructure.security;

import com.tfp.timetracking.identity.domain.InvalidCredentialsException;
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

    public IdentityAuthenticatedPrincipalStateChecker(UserRepository userRepository, TenantAccessRepository tenantAccessRepository) {
        this.userRepository = userRepository;
        this.tenantAccessRepository = tenantAccessRepository;
    }

    @Override
    public void ensureActivePrincipal(UUID tenantId, UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (!user.tenantId().equals(tenantId)) {
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new UserInactiveException();
        }
        if (!tenantAccessRepository.isActive(tenantId)) {
            throw new TenantInactiveException();
        }
    }
}
