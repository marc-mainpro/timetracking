package com.tfp.timetracking.identity.infrastructure;

import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.notification.application.NotificationRecipient;
import com.tfp.timetracking.notification.application.RoleRecipientQuery;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementacion en {@code identity} del puerto que declara
 * {@code notification} para el fan-out por rol (T170-01).
 *
 * <p>Vive aqui por la misma razon que {@link IdentityUserDirectoryQuery}: la
 * dependencia entre modulos apunta del que sabe hacia el que pregunta.
 */
@Component
public class IdentityRoleRecipientQuery implements RoleRecipientQuery {

    private final UserRepository userRepository;

    public IdentityRoleRecipientQuery(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<NotificationRecipient> findActiveByRole(UUID tenantId, String role) {
        Role parsedRole = parse(role);
        if (parsedRole == null || tenantId == null) {
            return List.of();
        }
        return userRepository.findActiveByRole(tenantId, parsedRole).stream()
                .map(IdentityRoleRecipientQuery::toRecipient)
                .toList();
    }

    @Override
    public List<NotificationRecipient> findActivePlatformAdmins() {
        return findActiveByRole(PlatformTenant.ID, Role.PLATFORM_ADMIN.name());
    }

    /**
     * Un rol desconocido no es un error del sistema de notificaciones: devuelve
     * cero destinatarios, igual que un tenant sin nadie con ese rol.
     */
    private static Role parse(String role) {
        if (role == null) {
            return null;
        }
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException notARole) {
            return null;
        }
    }

    private static NotificationRecipient toRecipient(User user) {
        return new NotificationRecipient(user.id(), user.email().value());
    }
}
