package com.tfp.timetracking.notification.application;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envia una notificacion y registra el resultado del intento (T110-05).
 *
 * <p>Cada notificacion se procesa en <b>su propia transaccion</b>
 * ({@code REQUIRES_NEW}): un fallo al enviar una no puede deshacer el registro
 * del intento de las demas del lote, que es lo que ocurriria si todas
 * compartieran la transaccion del job.
 *
 * <p>Nunca propaga el fallo: agotar los reintentos deja la notificacion en
 * {@code FAILED}, que es un desenlace previsto y no un error del proceso. La
 * notificacion sigue visible en la aplicacion.
 */
@Service
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final NotificationRepository notificationRepository;
    private final NotificationEmailComposer composer;
    private final NotificationDeliveryProperties properties;
    private final Clock clock;

    public NotificationSender(
            NotificationRepository notificationRepository,
            NotificationEmailComposer composer,
            NotificationDeliveryProperties properties,
            Clock clock) {
        this.notificationRepository = notificationRepository;
        this.composer = composer;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return {@code true} si el correo salio en este intento */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deliver(Notification notification) {
        if (!notification.isDeliverable()) {
            return false;
        }
        try {
            composer.send(notification);
            notification.markSent(clock.now());
            notificationRepository.save(notification);
            return true;
        } catch (RuntimeException ex) {
            notification.markAttemptFailed(ex.getMessage(), properties.maxAttempts(), clock.now());
            notificationRepository.save(notification);
            log.warn(
                    "notification.delivery.failed notificationId={} attempts={} status={}",
                    notification.id(),
                    notification.attempts(),
                    notification.status());
            return false;
        }
    }
}
