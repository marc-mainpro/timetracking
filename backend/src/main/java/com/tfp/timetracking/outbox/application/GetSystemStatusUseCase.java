package com.tfp.timetracking.outbox.application;

import com.tfp.timetracking.outbox.application.QueueStatusContributor.QueueStatus;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estado tecnico del sistema para el panel de plataforma (T140-05, RO-004).
 *
 * <p>Responde a la unica pregunta que justifica el panel: <b>¿hay algo atascado
 * que nadie esta mirando?</b> Lo que agota sus reintentos no vuelve a
 * intentarse solo y no produce ningun error visible para el usuario, asi que
 * sin panel la unica forma de enterarse seria consultar la base de datos a mano.
 *
 * <p>Reune las colas por contribucion ({@link QueueStatusContributor}) en vez de
 * consultar el repositorio de cada modulo: eso cerraria un ciclo con
 * {@code notification}, que ya depende de {@code outbox}.
 */
@Service
public class GetSystemStatusUseCase {

    private final List<QueueStatusContributor> contributors;

    public GetSystemStatusUseCase(List<QueueStatusContributor> contributors) {
        this.contributors = List.copyOf(contributors);
    }

    @Transactional(readOnly = true)
    public SystemStatus get() {
        List<QueueStatus> queues = contributors.stream()
                .map(QueueStatusContributor::status)
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .toList();
        return new SystemStatus(queues);
    }

    /** @param queues estado de cada cola, en orden estable para que el panel no baile */
    public record SystemStatus(List<QueueStatus> queues) {

        public SystemStatus {
            queues = List.copyOf(queues);
        }

        /** Hay trabajo humano pendiente: alguna cola agoto sus reintentos. */
        public boolean needsAttention() {
            return queues.stream().anyMatch(queue -> queue.failed() > 0);
        }

        public long totalFailed() {
            return queues.stream().mapToLong(QueueStatus::failed).sum();
        }
    }
}
