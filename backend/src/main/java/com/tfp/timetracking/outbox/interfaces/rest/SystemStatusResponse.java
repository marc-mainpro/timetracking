package com.tfp.timetracking.outbox.interfaces.rest;

import java.util.List;

/**
 * Estado tecnico del sistema (T140-05).
 *
 * @param needsAttention alguna cola agoto sus reintentos y requiere
 *     intervencion: es el unico campo que exige actuar, el resto es contexto
 */
public record SystemStatusResponse(List<QueueStatusResponse> queues, long totalFailed, boolean needsAttention) {

    public record QueueStatusResponse(String name, long pending, long failed) {}
}
