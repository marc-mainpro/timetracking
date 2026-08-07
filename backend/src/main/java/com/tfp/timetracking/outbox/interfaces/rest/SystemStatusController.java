package com.tfp.timetracking.outbox.interfaces.rest;

import com.tfp.timetracking.outbox.application.GetSystemStatusUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel técnico de plataforma (T140-05, RO-004).
 *
 * <p>Solo {@code PLATFORM_ADMIN}: informa del estado de todos los tenants, así
 * que no es tenant-scoped y no debe verlo un administrador de tenant.
 */
@RestController
@RequestMapping("/api/v1/platform/system-status")
@Tag(name = "Platform - System status")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class SystemStatusController {

    private final GetSystemStatusUseCase getSystemStatusUseCase;

    public SystemStatusController(GetSystemStatusUseCase getSystemStatusUseCase) {
        this.getSystemStatusUseCase = getSystemStatusUseCase;
    }

    @GetMapping
    @Operation(summary = "Estado técnico del sistema: outbox y notificaciones atascadas")
    public SystemStatusResponse get() {
        GetSystemStatusUseCase.SystemStatus status = getSystemStatusUseCase.get();
        return new SystemStatusResponse(
                status.queues().stream()
                        .map(queue -> new SystemStatusResponse.QueueStatusResponse(
                                queue.name(), queue.pending(), queue.failed()))
                        .toList(),
                status.totalFailed(),
                status.needsAttention());
    }
}
