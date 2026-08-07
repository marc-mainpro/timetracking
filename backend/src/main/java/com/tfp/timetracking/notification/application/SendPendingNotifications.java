package com.tfp.timetracking.notification.application;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Envia por correo las notificaciones pendientes (T110-05, RF-NOT-002,
 * RF-NOT-005).
 *
 * <p>Va en una tarea aparte del consumo del evento a proposito: crear la
 * notificacion es una escritura local que no debe quedar a expensas de que el
 * SMTP responda. Si el envio formara parte del consumo, un servidor de correo
 * lento retrasaria el procesado del outbox y uno caido haria fallar el mensaje
 * entero, cuando la notificacion en la aplicacion ya es util por si sola.
 */
@Service
public class SendPendingNotifications {

    private static final Logger log = LoggerFactory.getLogger(SendPendingNotifications.class);

    private final NotificationRepository notificationRepository;
    private final NotificationSender notificationSender;
    private final NotificationDeliveryProperties properties;

    public SendPendingNotifications(
            NotificationRepository notificationRepository,
            NotificationSender notificationSender,
            NotificationDeliveryProperties properties) {
        this.notificationRepository = notificationRepository;
        this.notificationSender = notificationSender;
        this.properties = properties;
    }

    /** @return numero de notificaciones enviadas correctamente en este lote */
    public int run() {
        List<Notification> pending = notificationRepository.findPendingForDelivery(properties.batchSize());
        int sent = 0;
        for (Notification notification : pending) {
            if (notificationSender.deliver(notification)) {
                sent++;
            }
        }
        if (!pending.isEmpty()) {
            log.info("notification.delivery.batch size={} sent={}", pending.size(), sent);
        }
        return sent;
    }
}
