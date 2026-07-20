package com.tfp.timetracking.outbox.infrastructure;

import com.tfp.timetracking.outbox.application.PublishPendingOutboxMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara periodicamente el publicador de outbox (T703, ADR-0005). El
 * intervalo es configurable via {@code outbox.poll-interval} (por defecto
 * 5 s, formato ISO-8601 tipo {@code PT5S}).
 *
 * <p>No contiene logica propia: delega enteramente en {@link
 * PublishPendingOutboxMessages}, que tambien es invocable directamente
 * (fuera del scheduler) desde tests u otras herramientas operativas.
 *
 * <p>Condicionado a {@code outbox.scheduler-enabled} (por defecto {@code
 * true}); en el perfil {@code test} se desactiva ({@code
 * application-test.yml}) para que las suites de otras funcionalidades no
 * compitan con un publicador en segundo plano sobre filas de {@code
 * outbox_message} que no les conciernen. Ejecutar dos instancias de este
 * job (o dos nodos de la aplicacion) en paralelo es seguro: {@code
 * claimBatch} usa {@code FOR UPDATE SKIP LOCKED} (T701), por lo que nunca
 * publican el mismo mensaje dos veces.
 */
@Component
@ConditionalOnProperty(prefix = "outbox", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);

    private final PublishPendingOutboxMessages publishPendingOutboxMessages;

    public OutboxPublisherJob(PublishPendingOutboxMessages publishPendingOutboxMessages) {
        this.publishPendingOutboxMessages = publishPendingOutboxMessages;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval:PT5S}")
    public void publishPending() {
        int claimed = publishPendingOutboxMessages.publishBatch();
        if (claimed > 0) {
            log.debug("outbox.publisher.batch claimed={}", claimed);
        }
    }
}
