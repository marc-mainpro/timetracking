package com.tfp.timetracking.outbox.application;

import com.tfp.timetracking.shared.domain.IntegrationEvent;

/**
 * Punto de extension opcional (T704) para observar los eventos de
 * integracion en el mismo instante en que {@link IntegrationEventPublisher}
 * los entrega. Su unico proposito en el MVP es servir de enganche para el
 * consumidor de demostracion idempotente
 * ({@code outbox.infrastructure.demo.DemoIdempotentEventConsumer}), que
 * ilustra el patron de deduplicacion por {@code eventId} que ADR-0005 exige
 * a los futuros consumidores reales.
 *
 * <p><strong>No es el mecanismo de entrega a consumidores externos reales.</strong>
 * Mientras ADR-0005 siga vigente (sin broker), la unica forma "real" de
 * consumir los eventos de integracion es leer el log estructurado que emite
 * {@code LoggingIntegrationEventPublisher}. Este listener corre en el mismo
 * proceso y en la misma invocacion que ese publicador, asi que no aporta
 * ninguna garantia adicional de entrega (si el proceso muere entre el log y
 * la notificacion al listener, el propio outbox seguira reintentando el
 * mensaje en la siguiente pasada, y el listener sera invocado de nuevo,
 * exactamente el escenario de redelivery que este mismo consumidor de
 * demostracion existe para probar).
 *
 * <p>Un fallo en un listener nunca debe impedir que el mensaje se marque
 * {@code PUBLISHED} (ver {@code LoggingIntegrationEventPublisher#publish}):
 * el listener es una observacion adicional, no parte del contrato de
 * publicacion en si.
 */
public interface IntegrationEventListener {

    void onEvent(IntegrationEvent event);
}
