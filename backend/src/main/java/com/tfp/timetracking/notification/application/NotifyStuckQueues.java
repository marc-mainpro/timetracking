package com.tfp.timetracking.notification.application;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.notification.domain.NotificationType;
import com.tfp.timetracking.outbox.application.GetSystemStatusUseCase;
import com.tfp.timetracking.outbox.application.GetSystemStatusUseCase.SystemStatus;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avisa a los administradores de plataforma cuando alguna cola agota sus
 * reintentos (T170-08).
 *
 * <p>Es la unica notificacion que no nace de un hecho de negocio, sino de una
 * condicion sostenida en el tiempo, y de ahi su unica dificultad: <b>sin
 * antirrepeticion, el aviso se emitiria en cada pasada</b> mientras el problema
 * siguiera sin resolverse. El disparo es el flanco —de «todo bien» a «hay
 * fallos»—, no el estado.
 *
 * <p>El flanco se recuerda en memoria y no en base de datos. Es una decision
 * consciente: un reinicio del proceso vuelve a avisar de una condicion que
 * siguiera activa, que es preferible al caso contrario —perder el aviso— y no
 * justifica una tabla nueva para un dato que solo vive mientras el proceso
 * vigila. Con varias instancias, cada una avisaria una vez.
 */
@Service
public class NotifyStuckQueues {

    private static final Logger log = LoggerFactory.getLogger(NotifyStuckQueues.class);

    private final GetSystemStatusUseCase getSystemStatus;
    private final RoleRecipientQuery roleRecipientQuery;
    private final NotificationRepository notificationRepository;
    private final Clock clock;
    private final IdGenerator idGenerator;

    /** Cierto mientras el aviso de la condicion actual ya se emitio. */
    private final AtomicBoolean alerted = new AtomicBoolean();

    public NotifyStuckQueues(
            GetSystemStatusUseCase getSystemStatus,
            RoleRecipientQuery roleRecipientQuery,
            NotificationRepository notificationRepository,
            Clock clock,
            IdGenerator idGenerator) {
        this.getSystemStatus = getSystemStatus;
        this.roleRecipientQuery = roleRecipientQuery;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /** @return numero de avisos emitidos en esta pasada */
    @Transactional
    public int run() {
        SystemStatus status = getSystemStatus.get();
        if (!status.needsAttention()) {
            // La condicion se resolvio: el proximo atasco vuelve a avisar.
            alerted.set(false);
            return 0;
        }
        if (!alerted.compareAndSet(false, true)) {
            return 0;
        }
        String body = NotificationTexts.queueStuck(status.totalFailed());
        int notified = 0;
        for (NotificationRecipient recipient : roleRecipientQuery.findActivePlatformAdmins()) {
            notificationRepository.save(Notification.create(
                    PlatformTenant.ID,
                    recipient.userId(),
                    recipient.email(),
                    NotificationType.SYSTEM_QUEUE_STUCK,
                    "Hay mensajes fallidos sin recuperar",
                    body,
                    true,
                    "/platform/system-status",
                    clock.now(),
                    idGenerator));
            notified++;
        }
        log.warn("notification.queue-stuck.detected totalFailed={} notified={}", status.totalFailed(), notified);
        return notified;
    }
}
