package com.tfp.timetracking.identity.infrastructure;

import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.notification.application.UserDirectoryQuery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementacion en {@code identity} del puerto que declara
 * {@code notification} (T110-04, T170-12).
 *
 * <p>Vive aqui, y no en {@code notification}, para que la dependencia entre
 * modulos apunte del que sabe hacia el que pregunta: {@code notification} no
 * conoce como se almacenan los usuarios.
 *
 * <p>Ambas consultas usan la via tenant-aware de {@link UserRepository}: un
 * usuario de otro tenant devuelve vacio aunque el id exista, de modo que un
 * payload manipulado no puede filtrar el nombre ni el correo de nadie.
 */
@Component
public class IdentityUserDirectoryQuery implements UserDirectoryQuery {

    private final UserRepository userRepository;

    public IdentityUserDirectoryQuery(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<String> findEmail(UUID tenantId, UUID userId) {
        return userRepository.findById(tenantId, userId).map(User::email).map(Object::toString);
    }

    @Override
    public Optional<String> findDisplayName(UUID tenantId, UUID userId) {
        return userRepository.findById(tenantId, userId).map(IdentityUserDirectoryQuery::displayName);
    }

    /**
     * Nombre y apellidos. El agregado los exige no vacios al crearse, asi que no
     * hay que contemplar el caso de un usuario sin nombre.
     */
    private static String displayName(User user) {
        return user.firstName() + " " + user.lastName();
    }
}
