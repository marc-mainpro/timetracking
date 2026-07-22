package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.identity.domain.LastAdminException;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignRoleUseCase {

    private final UserRepository userRepository;
    private final TenantContext tenantContext;
    private final Clock clock;
    private final AuditRecorder auditRecorder;

    public AssignRoleUseCase(UserRepository userRepository, TenantContext tenantContext, Clock clock, AuditRecorder auditRecorder) {
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public User assign(EmployeeRolesCommand command) {
        User user = userRepository.findById(tenantContext.currentTenantId(), command.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado"));
        Set<Role> roles = command.roles().stream().map(Role::valueOf).collect(Collectors.toSet());
        if (user.isActive() && user.hasRole(Role.TENANT_ADMIN) && !roles.contains(Role.TENANT_ADMIN)) {
            userRepository.lockActiveAdmins(tenantContext.currentTenantId());
            if (userRepository.countActiveAdminsExcludingUser(tenantContext.currentTenantId(), user.id()) == 0) {
                throw new LastAdminException();
            }
        }
        user.assignRoles(roles, clock);
        User saved = userRepository.save(user);
        auditRecorder.record(
                "EMPLOYEE_ROLES_UPDATED",
                "User",
                saved.id(),
                java.util.Map.of("roles", saved.roles().stream().map(Enum::name).toList()));
        return saved;
    }
}
