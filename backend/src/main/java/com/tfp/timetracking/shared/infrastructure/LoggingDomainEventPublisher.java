package com.tfp.timetracking.shared.infrastructure;

import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementacion provisional del puerto {@link DomainEventPublisher}: se
 * limita a loguear los eventos de dominio recibidos.
 *
 * <p>NO es el mecanismo definitivo de entrega de eventos: T702 (Transactional
 * Outbox, ver ADR-0005 y CONTEXT-DOMINIO §4) sustituira esta clase por una
 * escritura de los eventos de integracion correspondientes en la tabla
 * {@code outbox_message}, en la misma transaccion que el cambio de negocio.
 * Hasta entonces, los eventos NO se entregan a ningun consumidor externo.
 */
@Component
public class LoggingDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventPublisher.class);

    @Override
    public void publish(List<Object> events) {
        for (Object event : events) {
            log.info("Domain event published (provisional log sink, see T702): {}", event);
        }
    }
}
