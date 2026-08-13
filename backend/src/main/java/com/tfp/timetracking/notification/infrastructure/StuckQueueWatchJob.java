package com.tfp.timetracking.notification.infrastructure;

import com.tfp.timetracking.notification.application.NotifyStuckQueues;
import com.tfp.timetracking.shared.application.ScheduledJobRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Vigila periodicamente si alguna cola se ha quedado atascada (T170-08).
 *
 * <p>Va mucho mas espaciado que el envio de notificaciones: no es trabajo
 * pendiente que convenga despachar cuanto antes, sino una comprobacion de
 * salud, y un atasco no se resuelve solo por mirarlo mas a menudo.
 *
 * <p>Se apaga con la misma propiedad que el resto del envio, que es lo que hace
 * el perfil de test para que la bateria sea determinista.
 */
@Component
@ConditionalOnProperty(
        prefix = "notification.delivery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class StuckQueueWatchJob {

    private final NotifyStuckQueues notifyStuckQueues;
    private final ScheduledJobRunner jobRunner;

    public StuckQueueWatchJob(NotifyStuckQueues notifyStuckQueues, ScheduledJobRunner jobRunner) {
        this.notifyStuckQueues = notifyStuckQueues;
        this.jobRunner = jobRunner;
    }

    @Scheduled(fixedDelayString = "${notification.queue-watch.poll-interval:PT15M}")
    public void watch() {
        jobRunner.run("notification-queue-watch", notifyStuckQueues::run);
    }
}
