package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetEmployeeUseCase {

    private final UserRepository userRepository;
    private final TenantContext tenantContext;

    public GetEmployeeUseCase(UserRepository userRepository, TenantContext tenantContext) {
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
    }

    public User get(UUID employeeId) {
        return userRepository.findById(tenantContext.currentTenantId(), employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado"));
    }
}
