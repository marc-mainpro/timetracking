package com.tfp.timetracking.outbox.infrastructure.demo;

import com.tfp.timetracking.outbox.application.IntegrationEventListener;
import com.tfp.timetracking.outbox.application.ProcessedEventStore;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor de <strong>ejemplo</strong> (T704), no un caso de uso de
 * negocio: existe unicamente para demostrar, dentro del propio backend, el
 * patron de idempotencia que ADR-0005 exige a cualquier consumidor real de
 * los eventos de integracion publicados por el outbox (entrega
 * at-least-once: el mismo {@code eventId} puede llegar mas de una vez).
 *
 * <p>Se engancha a {@link IntegrationEventListener}, notificado por
 * {@code LoggingIntegrationEventPublisher} justo despues de cada publicacion
 * "real" (el log estructurado, unico sink permitido por ADR-0005 en el
 * MVP). Para simular una redelivery deliberada (el escenario que un broker
 * real produciria) los tests invocan {@link #consume(IntegrationEvent)}
 * explicitamente una segunda vez con el mismo evento.
 *
 * <p>Patron aplicado: reservar el par {@code (eventId, consumidor)} en
 * {@link ProcessedEventStore} y aplicar el "efecto" de negocio (aqui,
 * incrementar un contador observable en tests) solo si la reserva es nuestra.
 * La reserva es atomica, asi que dos hilos procesando el mismo evento a la vez
 * no pueden aplicar el efecto dos veces. Un consumidor real sigue el mismo
 * patron: reservar y aplicar el efecto en la misma transaccion.
 */
@Component
public class DemoIdempotentEventConsumer implements IntegrationEventListener {

    /** Resultado de intentar consumir un evento: util para que los tests aserten sin ambiguedad. */
    public enum ConsumptionResult {
        PROCESSED,
        DUPLICATE_IGNORED
    }

    private static final Logger log = LoggerFactory.getLogger(DemoIdempotentEventConsumer.class);

    /** Identifica a este consumidor en la tabla de deduplicacion. */
    public static final String CONSUMER = "demo-idempotent-event-consumer";

    private final ProcessedEventStore processedEventStore;
    private final AtomicInteger effectsApplied = new AtomicInteger();

    public DemoIdempotentEventConsumer(ProcessedEventStore processedEventStore) {
        this.processedEventStore = processedEventStore;
    }

    @Override
    public void onEvent(IntegrationEvent event) {
        consume(event);
    }

    /**
     * Aplica el evento si {@code eventId} no se habia procesado antes; en
     * caso contrario, lo ignora silenciosamente (mismo comportamiento que
     * exigiria cualquier consumidor real bajo entrega at-least-once).
     */
    @Transactional
    public ConsumptionResult consume(IntegrationEvent event) {
        if (!processedEventStore.tryClaim(event.eventId(), CONSUMER)) {
            log.info(
                    "demo-consumer.duplicate-ignored eventId={} eventType={}",
                    event.eventId(),
                    event.eventType());
            return ConsumptionResult.DUPLICATE_IGNORED;
        }
        effectsApplied.incrementAndGet();
        log.info("demo-consumer.processed eventId={} eventType={}", event.eventId(), event.eventType());
        return ConsumptionResult.PROCESSED;
    }

    /** Numero total de efectos aplicados (no duplicados) desde que arranco este bean. Solo para tests. */
    public int effectsAppliedCount() {
        return effectsApplied.get();
    }
}
