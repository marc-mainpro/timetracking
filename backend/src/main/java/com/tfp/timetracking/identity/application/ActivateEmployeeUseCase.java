package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivateEmployeeUseCase {

    private final UserRepository userRepository;
    private final TenantContext tenantContext;
    private final Clock clock;

    public ActivateEmployeeUseCase(UserRepository userRepository, TenantContext tenantContext, Clock clock) {
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
    }

    @Transactional
    public User activate(UUID employeeId) {
        User user = userRepository.findById(tenantContext.currentTenantId(), employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado"));
        user.activate(clock);
        return userRepository.save(user);
    }
}
