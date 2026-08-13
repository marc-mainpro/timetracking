package com.tfp.timetracking.notification.application;

import java.util.List;
import java.util.UUID;

/**
 * Resuelve los destinatarios de una notificacion dirigida a un rol, no a una
 * persona concreta (T170-01).
 *
 * <p>Sigue el mismo patron que {@link UserDirectoryQuery}: el puerto se declara
 * aqui y lo implementa {@code identity}, que es quien conoce a los usuarios, de
 * modo que la dependencia apunta del que sabe hacia el que pregunta.
 *
 * <p>El rol viaja como texto y no como enum de {@code identity} para no
 * introducir una dependencia de {@code notification} hacia ese modulo.
 *
 * <p><b>Solo destinatarios activos.</b> Un administrador desactivado no debe
 * recibir avisos operativos de una organizacion en la que ya no puede entrar.
 */
public interface RoleRecipientQuery {

    /**
     * @param tenantId tenant cuyos usuarios se consultan
     * @param role nombre del rol, tal como lo declara {@code identity.domain.Role}
     * @return los usuarios activos de ese tenant con ese rol; lista vacia si no
     *     hay ninguno o el rol no existe
     */
    List<NotificationRecipient> findActiveByRole(UUID tenantId, String role);

    /**
     * Administradores de plataforma activos. Es un caso aparte porque no los
     * identifica un tenant de negocio, sino el tenant de sistema.
     */
    List<NotificationRecipient> findActivePlatformAdmins();
}
