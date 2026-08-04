package com.tfp.timetracking.audit.application;

import java.util.Map;
import java.util.UUID;

public interface AuditRecorder {

    /**
     * Registra un evento de auditoria atribuido al principal autenticado. Solo
     * puede usarse dentro de una peticion con JWT valido.
     */
    void record(String action, String entityType, UUID entityId, Map<String, Object> metadata);

    /**
     * Registra un evento de auditoria con tenant y actor explicitos, para los
     * flujos que ocurren <b>antes</b> de que exista un principal autenticado y
     * en los que por tanto no hay {@code TenantContext} del que leerlos. El caso
     * de uso es el bloqueo temporal de cuentas (RS-008): un login fallido debe
     * auditarse aunque —precisamente porque— la autenticacion no ha llegado a
     * producirse.
     *
     * @param actorUserId usuario contra el que se produce el intento; es tambien
     *     el sujeto de la accion cuando no hay otro actor identificable
     */
    void record(
            UUID tenantId,
            UUID actorUserId,
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> metadata);
}
