package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateEmployeeUseCase {

    private final UserRepository userRepository;
    private final TenantContext tenantContext;
    private final Clock clock;

    public UpdateEmployeeUseCase(UserRepository userRepository, TenantContext tenantContext, Clock clock) {
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
    }

    @Transactional
    public User update(UpdateEmployeeCommand command) {
        User user = userRepository.findById(tenantContext.currentTenantId(), command.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado"));
        user.updateProfile(command.firstName(), command.lastName(), clock);
        return userRepository.save(user);
    }
}
