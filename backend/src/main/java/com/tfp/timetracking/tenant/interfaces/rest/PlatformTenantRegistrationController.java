package com.tfp.timetracking.tenant.interfaces.rest;

import com.tfp.timetracking.shared.interfaces.rest.PageQuery;
import com.tfp.timetracking.tenant.application.ApproveTenantRegistrationUseCase;
import com.tfp.timetracking.tenant.application.ListTenantRegistrationsUseCase;
import com.tfp.timetracking.tenant.application.RejectTenantRegistrationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Revisión de solicitudes de alta desde la administración de plataforma
 * (T53-03, diseño §7.4). Todas las operaciones exigen {@code PLATFORM_ADMIN} y
 * quedan auditadas.
 */
@RestController
@RequestMapping("/api/v1/platform/registrations")
@Tag(name = "Platform - Tenant registrations")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformTenantRegistrationController {

    private final ListTenantRegistrationsUseCase listUseCase;
    private final ApproveTenantRegistrationUseCase approveUseCase;
    private final RejectTenantRegistrationUseCase rejectUseCase;
    private final TenantRegistrationRestMapper mapper;

    public PlatformTenantRegistrationController(
            ListTenantRegistrationsUseCase listUseCase,
            ApproveTenantRegistrationUseCase approveUseCase,
            RejectTenantRegistrationUseCase rejectUseCase,
            TenantRegistrationRestMapper mapper) {
        this.listUseCase = listUseCase;
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Lista las solicitudes de alta, con filtro opcional por estado")
    public PagedTenantRegistrationsResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status) {
        PageQuery pageQuery = PageQuery.of(page, size);
        return mapper.toPagedResponse(listUseCase.list(status, pageQuery.page(), pageQuery.size()));
    }

    @PostMapping("/{registrationId}/approve")
    @Operation(summary = "Aprueba una solicitud y crea el tenant en estado PENDING")
    public TenantRegistrationResponse approve(@PathVariable UUID registrationId) {
        return mapper.toResponse(approveUseCase.approve(registrationId));
    }

    @PostMapping("/{registrationId}/reject")
    @Operation(summary = "Rechaza una solicitud indicando el motivo")
    public TenantRegistrationResponse reject(
            @PathVariable UUID registrationId, @Valid @RequestBody RejectRegistrationRequest request) {
        return mapper.toResponse(rejectUseCase.reject(registrationId, request.reason()));
    }
}
