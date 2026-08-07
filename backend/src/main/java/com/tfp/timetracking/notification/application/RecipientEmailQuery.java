package com.tfp.timetracking.notification.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Resuelve el correo del destinatario de una notificacion.
 *
 * <p>El modulo {@code notification} no puede consultar el repositorio de
 * {@code identity}, que es quien conoce a los usuarios: la interaccion entre
 * modulos va por un puerto declarado aqui e implementado alli. Asi la
 * dependencia apunta hacia el consumidor y {@code notification} sigue sin saber
 * como se almacenan los usuarios.
 */
public interface RecipientEmailQuery {

    /** @return el correo del usuario, o vacio si no existe o no es de ese tenant */
    Optional<String> findEmail(UUID tenantId, UUID userId);
}
