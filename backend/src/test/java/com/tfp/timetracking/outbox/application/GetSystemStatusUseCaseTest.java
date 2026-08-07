package com.tfp.timetracking.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.outbox.application.GetSystemStatusUseCase.SystemStatus;
import com.tfp.timetracking.outbox.application.QueueStatusContributor.QueueStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class GetSystemStatusUseCaseTest {

    @Test
    void reportsEveryContributedQueue() {
        SystemStatus status = statusOf(new QueueStatus("outbox", 3, 1), new QueueStatus("notifications", 5, 2));

        assertThat(status.queues()).extracting(QueueStatus::name).containsExactly("notifications", "outbox");
        assertThat(status.totalFailed()).isEqualTo(3);
    }

    @Test
    void ordersQueuesSoThePanelDoesNotShuffle() {
        // El orden de inyeccion de los beans no esta garantizado; sin un orden
        // estable las filas del panel cambiarian de sitio entre recargas.
        assertThat(statusOf(new QueueStatus("outbox", 0, 0), new QueueStatus("notifications", 0, 0)).queues())
                .extracting(QueueStatus::name)
                .containsExactly("notifications", "outbox");
        assertThat(statusOf(new QueueStatus("notifications", 0, 0), new QueueStatus("outbox", 0, 0)).queues())
                .extracting(QueueStatus::name)
                .containsExactly("notifications", "outbox");
    }

    @Test
    void aBacklogPendingIsNotSomethingToAttend() {
        // Lo pendiente se procesa solo en la siguiente pasada del job; senalarlo
        // haria que el panel avisara constantemente y dejara de mirarse.
        assertThat(statusOf(new QueueStatus("outbox", 500, 0)).needsAttention()).isFalse();
    }

    @Test
    void anythingThatExhaustedItsRetriesNeedsAHuman() {
        // FAILED no se recupera solo y no produce ningun error visible.
        assertThat(statusOf(new QueueStatus("outbox", 0, 1)).needsAttention()).isTrue();
        assertThat(statusOf(new QueueStatus("outbox", 0, 0), new QueueStatus("notifications", 0, 1)).needsAttention())
                .isTrue();
    }

    @Test
    void worksWithoutAnyContributor() {
        assertThat(statusOf().queues()).isEmpty();
        assertThat(statusOf().needsAttention()).isFalse();
    }

    private static SystemStatus statusOf(QueueStatus... queues) {
        List<QueueStatusContributor> contributors = List.of(queues).stream()
                .map(queue -> (QueueStatusContributor) () -> queue)
                .toList();
        return new GetSystemStatusUseCase(contributors).get();
    }
}
