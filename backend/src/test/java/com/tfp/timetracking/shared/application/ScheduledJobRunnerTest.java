package com.tfp.timetracking.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ScheduledJobRunnerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ScheduledJobRunner runner = new ScheduledJobRunner(registry);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /** T140-02: el job no nace de una peticion HTTP, asi que se genera correlacion propia. */
    @Test
    void givesEveryExecutionItsOwnCorrelationIdAndUseCase() {
        List<String> correlationIds = new ArrayList<>();
        List<String> useCases = new ArrayList<>();

        runner.run("outbox-publisher", () -> {
            correlationIds.add(MDC.get(ObservabilityContext.CORRELATION_ID));
            useCases.add(MDC.get(ObservabilityContext.USE_CASE));
        });
        runner.run("outbox-publisher", () -> correlationIds.add(MDC.get(ObservabilityContext.CORRELATION_ID)));

        assertThat(correlationIds).hasSize(2).doesNotContainNull();
        assertThat(correlationIds.get(0)).isNotEqualTo(correlationIds.get(1));
        assertThat(useCases).containsExactly("job:outbox-publisher");
    }

    @Test
    void clearsTheContextAfterwardsSoThePooledThreadDoesNotLeakIt() {
        runner.run("outbox-archiver", () -> {});

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void returnsTheValueProducedByTheJob() {
        assertThat(runner.call("outbox-publisher", () -> 7)).isEqualTo(7);
    }

    @Test
    void countsSuccessfulExecutions() {
        runner.run("outbox-publisher", () -> {});

        assertThat(registry.get("jobs.executions")
                        .tag("job", "outbox-publisher")
                        .tag("result", "SUCCESS")
                        .counter()
                        .count())
                .isEqualTo(1.0);
        assertThat(registry.get("jobs.duration")
                        .tag("job", "outbox-publisher")
                        .tag("result", "SUCCESS")
                        .timer()
                        .count())
                .isEqualTo(1L);
    }

    @Test
    void countsFailedExecutionsAndRethrows() {
        assertThatThrownBy(() -> runner.run("outbox-publisher", () -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registry.get("jobs.executions")
                        .tag("job", "outbox-publisher")
                        .tag("result", "FAILURE")
                        .counter()
                        .count())
                .isEqualTo(1.0);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    /** Un job anidado o un hilo reutilizado no debe heredar el contexto anterior. */
    @Test
    void startsFromACleanContextEvenIfTheThreadCarriesLeftovers() {
        MDC.put(ObservabilityContext.TENANT_ID, "tenant-de-la-peticion-anterior");
        List<String> observedTenant = new ArrayList<>();

        runner.run("outbox-publisher", () -> observedTenant.add(MDC.get(ObservabilityContext.TENANT_ID)));

        assertThat(observedTenant).containsExactly((String) null);
    }
}
