package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.PagedResult;
import org.springframework.stereotype.Service;

@Service
public class ListEmployeesUseCase {

    private final UserRepository userRepository;
    private final TenantContext tenantContext;

    public ListEmployeesUseCase(UserRepository userRepository, TenantContext tenantContext) {
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
    }

    public PagedResult<User> list(int page, int size, UserStatus status) {
        return userRepository.findByTenant(tenantContext.currentTenantId(), status, page, size);
    }
}
