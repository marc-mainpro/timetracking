package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.audit.application.AuditRecorder;
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
    private final AuditRecorder auditRecorder;

    public ActivateEmployeeUseCase(UserRepository userRepository, TenantContext tenantContext, Clock clock, AuditRecorder auditRecorder) {
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public User activate(UUID employeeId) {
        User user = userRepository.findById(tenantContext.currentTenantId(), employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado"));
        user.activate(clock);
        User saved = userRepository.save(user);
        auditRecorder.record("EMPLOYEE_ACTIVATED", "User", saved.id(), java.util.Map.of());
        return saved;
    }
}
