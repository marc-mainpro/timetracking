package com.tfp.timetracking.outbox.interfaces.rest;

import java.util.List;

/** Pagina de elementos fallidos de una cola. */
public record PagedFailedQueueEntriesResponse(
        List<FailedQueueEntryResponse> content, int page, int size, long totalElements, int totalPages) {}
