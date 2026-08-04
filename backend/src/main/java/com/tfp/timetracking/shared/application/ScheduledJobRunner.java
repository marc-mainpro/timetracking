package com.tfp.timetracking.shared.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Envoltura comun de las tareas programadas (T140-02 y T140-04, ADR-0013).
 *
 * <p>Un {@code @Scheduled} no nace de ninguna peticion HTTP, asi que el
 * {@code CorrelationIdFilter} nunca llega a ejecutarse y sus lineas de log
 * salian sin {@code correlationId}: imposible reconstruir "que hizo el
 * publicador de outbox a las 03:00" a partir de una traza. Este runner le da a
 * cada ejecucion su propio identificador de correlacion (RNF-020) y su
 * {@code useCase}, y los limpia al terminar para que el hilo del pool no
 * arrastre el MDC a la ejecucion siguiente.
 *
 * <p>De paso instrumenta lo que RO-003 pide de los jobs, con el patron de
 * {@code OutboxMetrics} (Micrometer sobre el {@code MeterRegistry} inyectado):
 *
 * <ul>
 *   <li>{@code jobs.executions} (contador, tags {@code job} y {@code result}):
 *       ejecuciones terminadas, con o sin exito.
 *   <li>{@code jobs.duration} (timer, tags {@code job} y {@code result}):
 *       duracion de cada ejecucion.
 * </ul>
 *
 * <p>La excepcion se vuelve a lanzar despues de contarla: el runner observa, no
 * decide la politica de errores del job.
 */
@Component
public class ScheduledJobRunner {

    /** Prefijo del {@code useCase} de una tarea programada, para distinguirla de un endpoint. */
    static final String USE_CASE_PREFIX = "job:";

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobRunner.class);

    private final MeterRegistry registry;

    public ScheduledJobRunner(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Ejecuta {@code body} con contexto de diagnostico y metricas propias.
     *
     * @param jobName nombre estable del job (tag {@code job} de las metricas)
     * @param body cuerpo del job
     * @return lo que devuelva {@code body}
     */
    public <T> T call(String jobName, Supplier<T> body) {
        ObservabilityContext.clear();
        ObservabilityContext.put(ObservabilityContext.CORRELATION_ID, ObservabilityContext.newCorrelationId());
        ObservabilityContext.put(ObservabilityContext.USE_CASE, USE_CASE_PREFIX + jobName);
        Timer.Sample sample = Timer.start(registry);
        String result = ObservabilityContext.RESULT_FAILURE;
        try {
            T value = body.get();
            result = ObservabilityContext.RESULT_SUCCESS;
            return value;
        } finally {
            ObservabilityContext.put(ObservabilityContext.RESULT, result);
            // Una linea de resumen por ejecucion: es la que lleva el campo
            // `result` de RO-002 y la que permite buscar por correlationId
            // todo lo que hizo una ejecucion concreta del job.
            log.debug("job.completed job={} result={}", jobName, result);
            sample.stop(Timer.builder("jobs.duration")
                    .description("Duracion de cada ejecucion de una tarea programada")
                    .tag("job", jobName)
                    .tag("result", result)
                    .register(registry));
            Counter.builder("jobs.executions")
                    .description("Ejecuciones de tareas programadas, con y sin exito")
                    .tag("job", jobName)
                    .tag("result", result)
                    .register(registry)
                    .increment();
            ObservabilityContext.clear();
        }
    }

    /** Variante para jobs sin valor de retorno. */
    public void run(String jobName, Runnable body) {
        call(jobName, () -> {
            body.run();
            return null;
        });
    }
}
