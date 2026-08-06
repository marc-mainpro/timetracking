package com.tfp.timetracking.notification.domain;

import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de notificaciones. Toda consulta de usuario es tenant-scoped. */
public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID tenantId, UUID id);

    PagedResult<Notification> findByRecipient(UUID tenantId, UUID recipientUserId, int page, int size);

    long countUnreadByRecipient(UUID tenantId, UUID recipientUserId);

    /**
     * Notificaciones pendientes de envio, en orden de creacion.
     *
     * <p>No lleva {@code tenantId}: la ejecuta una tarea programada, no una
     * peticion de usuario, y necesita recorrer todos los tenants. Es la misma
     * excepcion justificada que ya aplica el publicador del outbox.
     */
    List<Notification> findPendingForDelivery(int limit);
}
