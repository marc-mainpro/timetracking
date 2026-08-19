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

    /**
     * Cuenta las notificaciones en un estado de entrega, en todos los tenants.
     *
     * <p>No lleva {@code tenantId}: la consume el panel técnico de plataforma,
     * que vigila la salud del envío del sistema entero, no la de un tenant.
     */
    long countByStatus(NotificationStatus status);

    /**
     * Notificaciones en un estado de entrega, de la mas antigua a la mas
     * reciente, en todos los tenants.
     *
     * <p>No lleva {@code tenantId} por la misma razon que {@link
     * #countByStatus(NotificationStatus)}: la consume el panel de plataforma,
     * que vigila el envio del sistema entero. No debe reutilizarse desde ningun
     * flujo de usuario.
     */
    PagedResult<Notification> findByStatus(NotificationStatus status, int page, int size);

    /**
     * Busca una notificacion por id sin acotar por tenant.
     *
     * <p>Existe solo para las operaciones manuales del panel de plataforma, que
     * actuan sobre elementos de cualquier tenant y por tanto no tienen un
     * {@code tenantId} propio del que partir. Se declara explicitamente en vez
     * de dejar que el llamante aporte un {@code tenantId} recibido del cliente,
     * que es justo lo que la multitenancy prohibe.
     */
    Optional<Notification> findByIdForPlatform(UUID id);

    /**
     * Reencola una notificacion solo si sigue fallida. La guarda se ejecuta en
     * la misma sentencia para no sobrescribir una intervencion concurrente.
     */
    boolean requeueFailed(UUID id);

    /** Descarta una notificacion solo si sigue fallida, conservando su error. */
    boolean discardFailed(UUID id);

    /**
     * Cuenta el trabajo realmente encolado para envío: mismo filtro que
     * {@link #findPendingForDelivery(int)}.
     *
     * <p>Existe porque {@code countByStatus(PENDING)} <b>no</b> mide la cola
     * desde T170-02. Una notificación solo in-app nace {@code PENDING} y se
     * queda así para siempre —nunca se envía, así que nada la mueve de estado—,
     * de modo que contarla haría crecer el pendiente del panel sin techo y sin
     * que hubiera nada atascado.
     */
    long countPendingForDelivery();
}
