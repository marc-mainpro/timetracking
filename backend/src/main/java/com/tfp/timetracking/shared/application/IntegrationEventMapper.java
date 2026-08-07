package com.tfp.timetracking.shared.application;

import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.Optional;

/**
 * Puerto de traduccion de eventos de dominio a eventos de integracion
 * versionados (ADR-0005, ADR-0011).
 *
 * <p>Cada modulo de negocio aporta <b>su propia</b> implementacion como
 * {@code @Component} en {@code <modulo>.application.integration}. El publicador
 * de outbox las recibe todas inyectadas como {@code List<IntegrationEventMapper>}
 * y prueba una tras otra hasta que alguna sepa traducir el evento.
 *
 * <p>Antes esta composicion era una cadena literal de {@code .or(...)} dentro de
 * {@code OutboxDomainEventPublisher}, lo que obligaba a todo modulo nuevo a
 * editar una clase de <i>otro</i> modulo para darse de alta. Con la contribucion
 * por bean, anadir un modulo es anadir un fichero.
 *
 * <p>La interfaz vive en {@code shared.application} —y no en {@code outbox}—
 * para que los modulos de negocio no dependan del modulo de outbox: la
 * dependencia va siempre {@code outbox -> modulo}, nunca al reves, y asi no se
 * cierra el ciclo que prohibe {@code ModuleCyclesTest}.
 */
public interface IntegrationEventMapper {

    /**
     * @param domainEvent evento de dominio recogido tras persistir el agregado
     * @return el evento de integracion equivalente, o {@link Optional#empty()}
     *     si este modulo no sabe traducir ese evento de dominio
     */
    Optional<IntegrationEvent> map(Object domainEvent);
}
