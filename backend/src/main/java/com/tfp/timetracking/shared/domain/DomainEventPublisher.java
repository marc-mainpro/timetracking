package com.tfp.timetracking.shared.domain;

import java.util.List;

/**
 * Puerto de dominio para entregar los eventos de dominio acumulados por un
 * agregado tras persistirlo (CONTEXT-DOMINIO §3: "el caso de uso los recoge
 * tras persistir").
 *
 * <p>La implementacion real vive en infraestructura. En T203 la
 * implementacion es provisional (log); T702 la sustituira por una escritura
 * en la tabla {@code outbox_message} dentro de la misma transaccion de
 * negocio (Transactional Outbox, ADR-0005).
 */
public interface DomainEventPublisher {

    void publish(List<Object> events);
}
