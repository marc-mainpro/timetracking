package com.tfp.timetracking.notification.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Resuelve los datos de un usuario que una notificacion necesita nombrar
 * (T110-04, T170-12).
 *
 * <p>El modulo {@code notification} no puede consultar el repositorio de
 * {@code identity}, que es quien conoce a los usuarios: la interaccion entre
 * modulos va por un puerto declarado aqui e implementado alli. Asi la
 * dependencia apunta hacia el consumidor y {@code notification} sigue sin saber
 * como se almacenan los usuarios.
 *
 * <p>Empezo siendo {@code RecipientEmailQuery}, con el correo como unica
 * pregunta. Al redactar los textos aparecio la segunda —como se llama esta
 * persona— y el nombre viejo dejo de ser cierto: son dos datos del mismo
 * directorio, no dos consultas distintas.
 */
public interface UserDirectoryQuery {

    /** @return el correo del usuario, o vacio si no existe o no es de ese tenant */
    Optional<String> findEmail(UUID tenantId, UUID userId);

    /**
     * Nombre con el que referirse a la persona en un texto dirigido a otra.
     *
     * <p>Lo consulta el consumidor <b>al crear</b> la notificacion, no el
     * emisor al enviarla: el cuerpo se redacta una vez y queda congelado, que es
     * el mismo criterio por el que el agregado guarda el correo desnormalizado.
     *
     * @return el nombre completo, o vacio si el usuario no existe o no es de ese
     *     tenant; quien lo llama debe tener preparada una frase alternativa que
     *     no deje un hueco en el texto
     */
    Optional<String> findDisplayName(UUID tenantId, UUID userId);
}
