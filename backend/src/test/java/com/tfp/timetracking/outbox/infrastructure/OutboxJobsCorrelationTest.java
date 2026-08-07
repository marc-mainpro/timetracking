package com.tfp.timetracking.outbox.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.outbox.application.ArchivePublishedOutboxMessages;
import com.tfp.timetracking.outbox.application.PublishPendingOutboxMessages;
import com.tfp.timetracking.shared.application.ObservabilityContext;
import com.tfp.timetracking.shared.application.ScheduledJobRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * T140-02: las tareas programadas del outbox corrian sin {@code correlationId}
 * porque no nacen de ninguna peticion HTTP y {@code CorrelationIdFilter} nunca
 * llega a ejecutarse para ellas.
 */
class OutboxJobsCorrelationTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ScheduledJobRunner runner = new ScheduledJobRunner(registry);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void publisherJobRunsWithCorrelationIdAndUseCase() {
        PublishPendingOutboxMessages publish = mock(PublishPendingOutboxMessages.class);
        List<String> correlationIds = new ArrayList<>();
        List<String> useCases = new ArrayList<>();
        when(publish.publishBatch()).thenAnswer(invocation -> {
            correlationIds.add(MDC.get(ObservabilityContext.CORRELATION_ID));
            useCases.add(MDC.get(ObservabilityContext.USE_CASE));
            return 2;
        });

        new OutboxPublisherJob(publish, runner).publishPending();

        assertThat(correlationIds).hasSize(1).doesNotContainNull();
        assertThat(useCases).containsExactly("job:" + OutboxPublisherJob.JOB_NAME);
        assertThat(registry.get("jobs.executions")
                        .tag("job", OutboxPublisherJob.JOB_NAME)
                        .tag("result", "SUCCESS")
                        .counter()
                        .count())
                .isEqualTo(1.0);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void archiverJobRunsWithCorrelationIdAndUseCase() {
        ArchivePublishedOutboxMessages archive = mock(ArchivePublishedOutboxMessages.class);
        List<String> correlationIds = new ArrayList<>();
        List<String> useCases = new ArrayList<>();
        when(archive.archive()).thenAnswer(invocation -> {
            correlationIds.add(MDC.get(ObservabilityContext.CORRELATION_ID));
            useCases.add(MDC.get(ObservabilityContext.USE_CASE));
            return 0;
        });

        new OutboxArchiverJob(archive, runner).archivePublished();

        verify(archive).archive();
        assertThat(correlationIds).hasSize(1).doesNotContainNull();
        assertThat(useCases).containsExactly("job:" + OutboxArchiverJob.JOB_NAME);
        assertThat(registry.get("jobs.executions")
                        .tag("job", OutboxArchiverJob.JOB_NAME)
                        .tag("result", "SUCCESS")
                        .counter()
                        .count())
                .isEqualTo(1.0);
    }
}
