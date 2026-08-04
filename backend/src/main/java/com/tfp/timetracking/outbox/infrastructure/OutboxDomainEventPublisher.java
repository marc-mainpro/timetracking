package com.tfp.timetracking.outbox.infrastructure;

import com.tfp.timetracking.outbox.application.OutboxWriter;
import com.tfp.timetracking.shared.application.IntegrationEventMapper;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Implementacion definitiva del puerto {@link DomainEventPublisher}
 * (T702, ADR-0005, CONTEXT-DOMINIO §4). Sustituye a la provisional
 * {@code LoggingDomainEventPublisher} de T203, que ha sido eliminada: no se
 * ha mantenido como fallback porque dejar dos implementaciones candidatas del
 * mismo puerto (una que persiste, otra que solo loguea) es una fuente de
 * bugs de despliegue si el perfil equivocado queda activo por error, y no
 * hay ningun caso de uso real en este proyecto para "eventos de dominio que
 * no se persisten" (ver report de T702).
 *
 * <p>Por cada evento de dominio recibido, prueba los {@link IntegrationEventMapper}
 * aportados por los modulos de negocio hasta encontrar uno que sepa traducirlo a
 * {@link IntegrationEvent}; si ninguno aplica (p.ej. {@code BreakStarted}/{@code
 * BreakEnded}, que no se publican como integracion) el evento se descarta en
 * silencio. Cuando hay traduccion, escribe el envelope en el outbox via
 * {@link OutboxWriter}, que persiste dentro de la misma transaccion en la
 * que el caso de uso invocante llamo a {@code publish(...)} (nunca hay commit
 * ni I/O de red aqui: solo persistencia JPA participante de la transaccion
 * de negocio, ver ADR-0005).
 *
 * <p><b>Los mappers se inyectan como lista</b> (ADR-0011) en lugar de estar
 * enumerados aqui en una cadena de {@code .or(...)}: asi un modulo nuevo se da
 * de alta anadiendo su propio {@code @Component}, sin editar esta clase, que
 * pertenece a otro modulo. El orden de la lista es irrelevante porque un mismo
 * evento de dominio solo lo traduce el mapper de su propio modulo.
 *
 * <p>Esta clase vive en {@code outbox.infrastructure} (y no en
 * {@code shared.infrastructure}) a proposito: los modulos de negocio no
 * dependen de aqui, solo exponen sus mappers en su propia capa
 * {@code application.integration} (ver {@code OutboxEncapsulationTest}).
 * {@link IntegrationEvent} y {@link IntegrationEventMapper}, los tipos que
 * conectan ambos lados, viven en {@code shared} precisamente para que la
 * dependencia {@code outbox -> modulo de negocio} no cierre un ciclo
 * {@code outbox -> modulo -> outbox} (prohibido por {@code ModuleCyclesTest}).
 */
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxWriter outboxWriter;
    private final List<IntegrationEventMapper> mappers;

    public OutboxDomainEventPublisher(OutboxWriter outboxWriter, List<IntegrationEventMapper> mappers) {
        this.outboxWriter = outboxWriter;
        this.mappers = List.copyOf(mappers);
    }

    @Override
    public void publish(List<Object> events) {
        for (Object event : events) {
            toIntegrationEvent(event).ifPresent(outboxWriter::write);
        }
    }

    private Optional<IntegrationEvent> toIntegrationEvent(Object domainEvent) {
        for (IntegrationEventMapper mapper : mappers) {
            Optional<IntegrationEvent> mapped = mapper.map(domainEvent);
            if (mapped.isPresent()) {
                return mapped;
            }
        }
        return Optional.empty();
    }
}
