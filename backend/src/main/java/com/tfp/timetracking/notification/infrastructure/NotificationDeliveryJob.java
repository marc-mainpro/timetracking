package com.tfp.timetracking.notification.infrastructure;

import com.tfp.timetracking.notification.application.SendPendingNotifications;
import com.tfp.timetracking.shared.application.ScheduledJobRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara el envio de las notificaciones pendientes (T110-05).
 *
 * <p>Se puede apagar con {@code notification.delivery.enabled=false}, que es lo
 * que hace el perfil de test: sin ello, un job de fondo enviando correo mientras
 * corre la bateria haria las pruebas no deterministas.
 */
@Component
@ConditionalOnProperty(
        prefix = "notification.delivery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationDeliveryJob {

    private final SendPendingNotifications sendPendingNotifications;
    private final ScheduledJobRunner jobRunner;

    public NotificationDeliveryJob(
            SendPendingNotifications sendPendingNotifications, ScheduledJobRunner jobRunner) {
        this.sendPendingNotifications = sendPendingNotifications;
        this.jobRunner = jobRunner;
    }

    @Scheduled(fixedDelayString = "${notification.delivery.poll-interval:PT30S}")
    public void deliverPending() {
        jobRunner.run("notification-delivery", sendPendingNotifications::run);
    }
}
