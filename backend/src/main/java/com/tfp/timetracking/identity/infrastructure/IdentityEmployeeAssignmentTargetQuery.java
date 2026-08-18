package com.tfp.timetracking.identity.infrastructure;

import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.application.EmployeeAssignmentTargetQuery;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementacion en {@code identity} del puerto que declara {@code shared}, al
 * modo de {@link IdentityUserDirectoryQuery}: vive aqui para que la dependencia
 * apunte del que sabe hacia el que pregunta.
 *
 * <p>Usa la via tenant-aware de {@link UserRepository}: un usuario de otro
 * tenant se responde como desconocido aunque el id exista.
 *
 * <p>No se exige que el usuario este activo. Desactivar a alguien no borra sus
 * turnos ni su calendario, y el sistema ya admite asignaciones sobre un
 * empleado inactivo; acotar por estado es cosa de los listados, que piden
 * {@code status=ACTIVE} al ofrecer destinatarios.
 */
@Component
public class IdentityEmployeeAssignmentTargetQuery implements EmployeeAssignmentTargetQuery {

    private final UserRepository userRepository;

    public IdentityEmployeeAssignmentTargetQuery(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public TargetStatus check(UUID tenantId, UUID userId) {
        return userRepository
                .findById(tenantId, userId)
                .map(user -> user.hasRole(Role.EMPLOYEE) ? TargetStatus.ASSIGNABLE : TargetStatus.NOT_EMPLOYEE)
                .orElse(TargetStatus.UNKNOWN);
    }
}
