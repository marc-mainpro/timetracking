package com.tfp.timetracking.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.outbox.application.FailedQueueMaintenance.FailedQueueEntry;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManageFailedQueueEntriesUseCaseTest {

    private static final UUID AFFECTED_TENANT = UUID.randomUUID();
    private static final UUID ENTRY_ID = UUID.randomUUID();

    @Mock
    private FailedQueueMaintenance outboxQueue;

    @Mock
    private AuditRecorder auditRecorder;

    @Test
    void anUnknownQueueIsNotFound() {
        // El nombre de la cola llega en la URL: un enlace viejo no debe
        // parecer una caida del servidor.
        ManageFailedQueueEntriesUseCase useCase = useCase();

        assertThatThrownBy(() -> useCase.listFailed("inventada", 0, 20)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> useCase.retry("inventada", ENTRY_ID)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> useCase.discard("inventada", ENTRY_ID, "motivo"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(auditRecorder, never()).record(any(), any(), any(), any());
    }

    @Test
    void listingDelegatesOnTheQueueThatOwnsIt() {
        ManageFailedQueueEntriesUseCase useCase = useCase();
        when(outboxQueue.listFailed(anyInt(), anyInt()))
                .thenReturn(new PagedResult<>(List.of(entry()), 0, 20, 1, 1));

        PagedResult<FailedQueueEntry> result = useCase.listFailed("outbox", 0, 20);

        assertThat(result.content()).hasSize(1);
        verify(outboxQueue).listFailed(0, 20);
    }

    @Test
    void retryingAuditsTheActionWithTheAffectedTenant() {
        ManageFailedQueueEntriesUseCase useCase = useCase();
        when(outboxQueue.retry(ENTRY_ID)).thenReturn(entry());

        useCase.retry("outbox", ENTRY_ID);

        Map<String, Object> metadata = auditedMetadata("PLATFORM_QUEUE_ENTRY_RETRIED");
        // El evento se atribuye al tenant de plataforma, asi que sin esto no
        // quedaria constancia de a quien afecto la intervencion.
        assertThat(metadata).containsEntry("affectedTenantId", AFFECTED_TENANT.toString());
        assertThat(metadata).containsEntry("queue", "outbox");
        assertThat(metadata).doesNotContainKey("reason");
    }

    @Test
    void discardingAuditsTheReason() {
        ManageFailedQueueEntriesUseCase useCase = useCase();
        when(outboxQueue.discard(ENTRY_ID)).thenReturn(entry());

        useCase.discard("outbox", ENTRY_ID, "  duplicado de otro evento  ");

        Map<String, Object> metadata = auditedMetadata("PLATFORM_QUEUE_ENTRY_DISCARDED");
        // El motivo es la unica explicacion que quedara de por que se abandono.
        assertThat(metadata).containsEntry("reason", "duplicado de otro evento");
        assertThat(metadata).containsEntry("attempts", 8);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> auditedMetadata(String action) {
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditRecorder).record(eq(action), eq("QueueEntry"), eq(ENTRY_ID), metadata.capture());
        return metadata.getValue();
    }

    private ManageFailedQueueEntriesUseCase useCase() {
        when(outboxQueue.queueName()).thenReturn("outbox");
        return new ManageFailedQueueEntriesUseCase(List.of(outboxQueue), auditRecorder);
    }

    private static FailedQueueEntry entry() {
        return new FailedQueueEntry(
                ENTRY_ID,
                AFFECTED_TENANT,
                "time-tracking.workday-closed.v1",
                "Workday#" + UUID.randomUUID(),
                8,
                "some error",
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
