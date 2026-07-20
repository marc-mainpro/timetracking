package com.tfp.timetracking.outbox.infrastructure;

import com.tfp.timetracking.corrections.application.integration.CorrectionsIntegrationEventMapper;
import com.tfp.timetracking.identity.application.integration.IdentityIntegrationEventMapper;
import com.tfp.timetracking.outbox.application.OutboxWriter;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.tenant.application.integration.TenantIntegrationEventMapper;
import com.tfp.timetracking.timetracking.application.integration.TimeTrackingIntegrationEventMapper;
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
 * <p>Por cada evento de dominio recibido, prueba el mapper de cada modulo de
 * negocio hasta encontrar uno que sepa traducirlo a {@link IntegrationEvent};
 * si ninguno aplica (p.ej. {@code BreakStarted}/{@code BreakEnded}, que no se
 * publican como integracion en este MVP) el evento se descarta en silencio.
 * Cuando hay traduccion, escribe el envelope en el outbox via
 * {@link OutboxWriter}, que persiste dentro de la misma transaccion en la
 * que el caso de uso invocante llamo a {@code publish(...)} (nunca hay commit
 * ni I/O de red aqui: solo persistencia JPA participante de la transaccion
 * de negocio, ver ADR-0005).
 *
 * <p>Esta clase vive en {@code outbox.infrastructure} (y no en
 * {@code shared.infrastructure}) a proposito: es la unica pieza que necesita
 * conocer los mappers de traduccion de todos los modulos de negocio, y
 * {@code shared} debe seguir sin depender de modulos de negocio concretos.
 * Los modulos de negocio, a su vez, no dependen de aqui: solo exponen sus
 * mappers en su propia capa {@code application.integration} y no importan
 * nada de {@code outbox.infrastructure} (ver {@code OutboxEncapsulationTest}).
 * {@link IntegrationEvent}, el tipo que conecta ambos lados, vive en
 * {@code shared.domain} (no en {@code outbox.domain}) precisamente para que
 * esta dependencia {@code outbox -> modulo de negocio} no cierre un ciclo
 * {@code outbox -> modulo -> outbox} (prohibido por {@code
 * ModuleCyclesTest}).
 */
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxWriter outboxWriter;

    public OutboxDomainEventPublisher(OutboxWriter outboxWriter) {
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void publish(List<Object> events) {
        for (Object event : events) {
            toIntegrationEvent(event).ifPresent(outboxWriter::write);
        }
    }

    private Optional<IntegrationEvent> toIntegrationEvent(Object domainEvent) {
        return TenantIntegrationEventMapper.map(domainEvent)
                .or(() -> IdentityIntegrationEventMapper.map(domainEvent))
                .or(() -> TimeTrackingIntegrationEventMapper.map(domainEvent))
                .or(() -> CorrectionsIntegrationEventMapper.map(domainEvent));
    }
}
