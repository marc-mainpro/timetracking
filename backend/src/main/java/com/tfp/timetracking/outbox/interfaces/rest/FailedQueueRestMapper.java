package com.tfp.timetracking.outbox.interfaces.rest;

import com.tfp.timetracking.outbox.application.FailedQueueMaintenance.FailedQueueEntry;
import com.tfp.timetracking.shared.domain.PagedResult;
import org.springframework.stereotype.Component;

/** Traduce los elementos fallidos a su representacion HTTP. */
@Component
public class FailedQueueRestMapper {

    /**
     * El error de un mensaje de outbox se guarda como {@code TEXT} sin limite y
     * puede traer una traza entera. Se recorta al entrar en la respuesta: lo que
     * identifica el fallo esta al principio, y arrastrar el resto hasta el
     * navegador solo aumenta la superficie por la que se escapan cadenas de
     * conexion o datos de negocio.
     */
    private static final int MAX_ERROR_LENGTH = 500;

    public PagedFailedQueueEntriesResponse toPagedResponse(PagedResult<FailedQueueEntry> result) {
        return new PagedFailedQueueEntriesResponse(
                result.content().stream().map(this::toResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    private FailedQueueEntryResponse toResponse(FailedQueueEntry entry) {
        return new FailedQueueEntryResponse(
                entry.id(),
                entry.tenantId(),
                entry.type(),
                entry.reference(),
                entry.attempts(),
                truncate(entry.lastError()),
                entry.occurredAt());
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH) + "…";
    }
}
