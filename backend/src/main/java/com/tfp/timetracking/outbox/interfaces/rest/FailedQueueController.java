package com.tfp.timetracking.outbox.interfaces.rest;

import com.tfp.timetracking.outbox.application.ManageFailedQueueEntriesUseCase;
import com.tfp.timetracking.shared.interfaces.rest.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Intervencion manual sobre las colas con reintentos, desde el panel tecnico de
 * plataforma.
 *
 * <p>Solo {@code PLATFORM_ADMIN}: opera sobre elementos de todos los tenants,
 * asi que no es tenant-scoped y no debe verlo un administrador de tenant.
 *
 * <p>Es una coleccion propia y no una subruta de {@code /system-status}: aquel
 * es un informe de solo lectura, y colgarle mutaciones mezclaria dos recursos
 * distintos.
 */
@RestController
@RequestMapping("/api/v1/platform/queues")
@Tag(name = "Platform - Failed queues")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class FailedQueueController {

    private final ManageFailedQueueEntriesUseCase manageFailedQueueEntriesUseCase;
    private final FailedQueueRestMapper mapper;

    public FailedQueueController(
            ManageFailedQueueEntriesUseCase manageFailedQueueEntriesUseCase, FailedQueueRestMapper mapper) {
        this.manageFailedQueueEntriesUseCase = manageFailedQueueEntriesUseCase;
        this.mapper = mapper;
    }

    @GetMapping("/{queue}/failed")
    @Operation(summary = "Lista los elementos de una cola que agotaron sus reintentos")
    public PagedFailedQueueEntriesResponse listFailed(
            @PathVariable String queue,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageQuery pageQuery = PageQuery.of(page, size);
        return mapper.toPagedResponse(
                manageFailedQueueEntriesUseCase.listFailed(queue, pageQuery.page(), pageQuery.size()));
    }

    @PostMapping("/{queue}/failed/{entryId}/retry")
    @Operation(summary = "Devuelve a la cola un elemento fallido")
    public ResponseEntity<Void> retry(@PathVariable String queue, @PathVariable UUID entryId) {
        manageFailedQueueEntriesUseCase.retry(queue, entryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{queue}/failed/{entryId}/discard")
    @Operation(summary = "Descarta un elemento fallido conservando su traza")
    public ResponseEntity<Void> discard(
            @PathVariable String queue, @PathVariable UUID entryId, @Valid @RequestBody DiscardQueueEntryRequest request) {
        manageFailedQueueEntriesUseCase.discard(queue, entryId, request.reason());
        return ResponseEntity.noContent().build();
    }
}
