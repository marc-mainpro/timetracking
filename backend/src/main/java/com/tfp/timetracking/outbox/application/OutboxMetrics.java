package com.tfp.timetracking.outbox.application;

import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Metricas Micrometer del publicador de outbox (T703), expuestas por
 * Actuator (ver {@code management.endpoints.web.exposure.include} en
 * {@code application.yml}):
 *
 * <ul>
 *   <li>{@code outbox.messages.published} (contador): mensajes publicados con exito.
 *   <li>{@code outbox.messages.failed} (contador): mensajes marcados {@code FAILED}
 *       tras agotar los reintentos.
 *   <li>{@code outbox.messages.retried} (contador): fallos de publicacion que
 *       programaron un reintento (no agotaron los intentos).
 *   <li>{@code outbox.messages.pending} (gauge): mensajes en {@code PENDING}
 *       o {@code PROCESSING} en este momento (backlog).
 *   <li>{@code outbox.publish.duration} (timer): duracion de cada llamada al
 *       puerto {@link IntegrationEventPublisher}.
 * </ul>
 */
@Component
public class OutboxMetrics {

    private final Counter published;
    private final Counter failed;
    private final Counter retried;
    private final Timer publishTimer;

    public OutboxMetrics(MeterRegistry registry, OutboxMessageRepository repository) {
        this.published = Counter.builder("outbox.messages.published")
                .description("Mensajes de outbox publicados con exito")
                .register(registry);
        this.failed = Counter.builder("outbox.messages.failed")
                .description("Mensajes de outbox marcados FAILED tras agotar los reintentos")
                .register(registry);
        this.retried = Counter.builder("outbox.messages.retried")
                .description("Fallos de publicacion que programaron un reintento")
                .register(registry);
        this.publishTimer = Timer.builder("outbox.publish.duration")
                .description("Duracion de cada intento de publicacion de un mensaje de outbox")
                .register(registry);
        registry.gauge("outbox.messages.pending", repository, OutboxMessageRepository::countPending);
    }

    void recordPublished() {
        published.increment();
    }

    void recordFailed() {
        failed.increment();
    }

    void recordRetried() {
        retried.increment();
    }

    Timer.Sample startTimer() {
        return Timer.start();
    }

    void stopTimer(Timer.Sample sample) {
        sample.stop(publishTimer);
    }
}
