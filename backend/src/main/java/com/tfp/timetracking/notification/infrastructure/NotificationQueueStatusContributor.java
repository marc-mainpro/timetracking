package com.tfp.timetracking.notification.infrastructure;

import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.notification.domain.NotificationStatus;
import com.tfp.timetracking.outbox.application.QueueStatusContributor;
import org.springframework.stereotype.Component;

/**
 * Estado de la cola de envio de notificaciones (T140-05).
 *
 * <p>Lo aporta {@code notification}, que es quien conoce sus estados, y no lo
 * consulta {@code outbox}: la dependencia entre ambos va en este sentido.
 */
@Component
public class NotificationQueueStatusContributor implements QueueStatusContributor {

    private final NotificationRepository notificationRepository;

    public NotificationQueueStatusContributor(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public QueueStatus status() {
        return new QueueStatus(
                "notifications",
                notificationRepository.countByStatus(NotificationStatus.PENDING),
                notificationRepository.countByStatus(NotificationStatus.FAILED));
    }
}
