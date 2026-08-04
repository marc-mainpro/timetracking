package com.tfp.timetracking.shared.infrastructure.observability;

import org.springframework.boot.actuate.health.Status;

/**
 * Estados de salud propios del proyecto (T140-03, ADR-0015).
 *
 * <p>Actuator solo trae {@code UP}, {@code DOWN}, {@code OUT_OF_SERVICE} y
 * {@code UNKNOWN}, y {@code DOWN} responde 503. Como {@code /actuator/health}
 * es la sonda del contenedor, con ese vocabulario la unica forma de senalar
 * "hay algo que mirar" es provocar el reinicio de una aplicacion que funciona.
 *
 * <p>{@link #DEGRADED} cubre ese hueco: aparece en la sonda operativa y en las
 * alertas, pero {@code config/observability.yml} lo mapea a HTTP 200 y lo
 * ordena por encima de {@code UP}, de modo que el agregado lo refleja sin que
 * el orquestador recicle nada.
 */
public final class HealthStatuses {

    /** La aplicacion sirve peticiones, pero algo requiere atencion operativa. */
    public static final Status DEGRADED = new Status("DEGRADED", "La aplicacion sirve, pero algo requiere atencion");

    private HealthStatuses() {
    }
}
