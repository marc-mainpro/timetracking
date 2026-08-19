package com.tfp.timetracking.outbox.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

/**
 * Un elemento fallido tal como lo ve el panel de plataforma.
 *
 * @param tenantId tenant afectado; null en los eventos que no son de un tenant
 * @param lastError ultimo error, truncado: una traza completa no cabe en la
 *     pantalla y solo sirve para arrastrar datos sensibles hasta el navegador
 */
public record FailedQueueEntryResponse(
        UUID id, UUID tenantId, String type, String reference, int attempts, String lastError, Instant occurredAt) {}
