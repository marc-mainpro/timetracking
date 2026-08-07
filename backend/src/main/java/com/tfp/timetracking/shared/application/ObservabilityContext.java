package com.tfp.timetracking.shared.application;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Contexto de diagnostico de una unidad de trabajo (T140-01/T140-02, ADR-0015).
 *
 * <p>Centraliza las <b>unicas</b> claves MDC que la aplicacion puede poner en
 * los logs. El formateador estructurado (ECS) vuelca el MDC entero como campos
 * de primer nivel del JSON, asi que el MDC es, de facto, el esquema del log: si
 * cualquiera pudiese anadir claves libremente, un descuido (una cookie, un
 * token, una contrasena) acabaria publicado en cada linea de la peticion. Por
 * eso las claves viven aqui, son las de RO-002 y ninguna admite contenido
 * sensible (RS-014):
 *
 * <ul>
 *   <li>{@code correlationId}: identificador de la peticion o de la ejecucion
 *       de un job (RNF-020).
 *   <li>{@code tenantId} / {@code userId}: identificadores opacos (UUID) del
 *       principal autenticado. Nunca el correo ni el nombre.
 *   <li>{@code useCase}: nombre estable de la operacion
 *       ({@code Controlador#metodo} o {@code job:<nombre>}).
 *   <li>{@code result}: desenlace de la unidad de trabajo
 *       ({@code SUCCESS} / {@code FAILURE}).
 * </ul>
 *
 * <p>El {@code timestamp} y el {@code level} de RO-002 los aporta el propio
 * formateador; no son claves MDC.
 *
 * <p>Vive en {@code shared.application} y no en {@code shared.infrastructure}
 * porque lo consumen tanto el filtro HTTP como {@link ScheduledJobRunner}, y la
 * arquitectura por capas prohibe que nadie dependa de {@code infrastructure}.
 * No arrastra Spring: solo SLF4J.
 */
public final class ObservabilityContext {

    /** Identificador de correlacion de la peticion o ejecucion (RNF-020). */
    public static final String CORRELATION_ID = "correlationId";

    /** UUID del tenant del principal autenticado. */
    public static final String TENANT_ID = "tenantId";

    /** UUID del usuario del principal autenticado. */
    public static final String USER_ID = "userId";

    /** Nombre estable del caso de uso en curso. */
    public static final String USE_CASE = "useCase";

    /** Desenlace de la unidad de trabajo. */
    public static final String RESULT = "result";

    /** Valor de {@link #RESULT} cuando la unidad de trabajo termina bien. */
    public static final String RESULT_SUCCESS = "SUCCESS";

    /** Valor de {@link #RESULT} cuando la unidad de trabajo termina en error. */
    public static final String RESULT_FAILURE = "FAILURE";

    private ObservabilityContext() {
    }

    /** Genera un identificador de correlacion nuevo. */
    public static String newCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /** @return el identificador de correlacion en curso, o {@code null} si no hay ninguno */
    public static String currentCorrelationId() {
        return MDC.get(CORRELATION_ID);
    }

    /**
     * Fija una clave del MDC ignorando valores nulos o en blanco, para que un
     * dato ausente no acabe como {@code "null"} en el log.
     *
     * @throws IllegalArgumentException si {@code key} no es una de las claves
     *     declaradas en esta clase (RS-014: el esquema del log es cerrado)
     */
    public static void put(String key, String value) {
        requireKnownKey(key);
        if (value == null || value.isBlank()) {
            return;
        }
        MDC.put(key, value);
    }

    /** Elimina del MDC todas las claves declaradas en esta clase. */
    public static void clear() {
        MDC.remove(CORRELATION_ID);
        MDC.remove(TENANT_ID);
        MDC.remove(USER_ID);
        MDC.remove(USE_CASE);
        MDC.remove(RESULT);
    }

    private static void requireKnownKey(String key) {
        if (!CORRELATION_ID.equals(key)
                && !TENANT_ID.equals(key)
                && !USER_ID.equals(key)
                && !USE_CASE.equals(key)
                && !RESULT.equals(key)) {
            throw new IllegalArgumentException("Clave MDC no declarada en ObservabilityContext: " + key);
        }
    }
}
