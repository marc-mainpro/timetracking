package com.tfp.timetracking.notification.application;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.notification.domain.NotificationStatus;
import com.tfp.timetracking.outbox.application.FailedQueueMaintenance;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantenimiento manual de la cola de envio de notificaciones.
 *
 * <p>Descartar aqui significa <b>renunciar al correo</b>, no borrar el aviso:
 * como toda notificacion fallida, una descartada sigue visible en la aplicacion
 * para su destinatario.
 */
@Component
public class NotificationFailedQueueMaintenance implements FailedQueueMaintenance {

    private final NotificationRepository repository;

    public NotificationFailedQueueMaintenance(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public String queueName() {
        return "notifications";
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<FailedQueueEntry> listFailed(int page, int size) {
        PagedResult<Notification> failed = repository.findByStatus(NotificationStatus.FAILED, page, size);
        return new PagedResult<>(
                failed.content().stream()
                        .map(NotificationFailedQueueMaintenance::toEntry)
                        .toList(),
                failed.page(),
                failed.size(),
                failed.totalElements(),
                failed.totalPages());
    }

    @Override
    @Transactional
    public FailedQueueEntry retry(UUID id) {
        Notification notification = load(id);
        FailedQueueEntry previous = toEntry(notification);
        notification.requeueDelivery();
        repository.save(notification);
        return previous;
    }

    @Override
    @Transactional
    public FailedQueueEntry discard(UUID id) {
        Notification notification = load(id);
        FailedQueueEntry previous = toEntry(notification);
        notification.discardDelivery();
        repository.save(notification);
        return previous;
    }

    private Notification load(UUID id) {
        return repository
                .findByIdForPlatform(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notificacion no encontrada: " + id));
    }

    /**
     * Ni el cuerpo ni el correo del destinatario salen del modulo: son datos
     * personales de otro tenant, y para intervenir en la cola basta con saber
     * de que tipo de aviso se trata y a quien iba dirigido.
     */
    private static FailedQueueEntry toEntry(Notification notification) {
        return new FailedQueueEntry(
                notification.id(),
                notification.tenantId(),
                notification.type().name(),
                notification.recipientUserId().toString(),
                notification.attempts(),
                notification.lastError(),
                notification.createdAt());
    }
}
