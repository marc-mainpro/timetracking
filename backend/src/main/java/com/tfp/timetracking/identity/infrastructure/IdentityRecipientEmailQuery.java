package com.tfp.timetracking.identity.infrastructure;

import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.notification.application.RecipientEmailQuery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementacion en {@code identity} del puerto que declara
 * {@code notification} (T110-04).
 *
 * <p>Vive aqui, y no en {@code notification}, para que la dependencia entre
 * modulos apunte del que sabe hacia el que pregunta: {@code notification} no
 * conoce como se almacenan los usuarios.
 */
@Component
public class IdentityRecipientEmailQuery implements RecipientEmailQuery {

    private final UserRepository userRepository;

    public IdentityRecipientEmailQuery(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<String> findEmail(UUID tenantId, UUID userId) {
        return userRepository.findById(tenantId, userId).map(User::email).map(Object::toString);
    }
}
