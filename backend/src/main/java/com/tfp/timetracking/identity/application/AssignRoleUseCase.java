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
        // tenantAssignableRoles rechaza PLATFORM_ADMIN: es un rol global y no debe
        // poder concederse desde la administracion de un tenant (T50-04, ADR-0010).
        Set<Role> roles = Role.tenantAssignableRoles(command.roles());
        if (user.isActive() && user.hasRole(Role.TENANT_ADMIN) && !roles.contains(Role.TENANT_ADMIN)) {
            // El bloqueo previo serializa a los administradores activos del tenant:
            // sin el, dos degradaciones simultaneas pueden contarse la una a la otra
            // como "todavia queda un admin" y dejar el tenant sin ninguno.
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
