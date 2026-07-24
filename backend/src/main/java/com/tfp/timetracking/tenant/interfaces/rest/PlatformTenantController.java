package com.tfp.timetracking.tenant.interfaces.rest;

import com.tfp.timetracking.shared.interfaces.rest.PageQuery;
import com.tfp.timetracking.tenant.application.ChangeTenantLifecycleUseCase;
import com.tfp.timetracking.tenant.application.GetTenantUseCase;
import com.tfp.timetracking.tenant.application.ListTenantsUseCase;
import com.tfp.timetracking.tenant.domain.TenantStatus;
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
 * API de administración de plataforma para el ciclo de vida de los tenants
 * (RF-TEN-001..008, T50-05). Todas las operaciones exigen el rol
 * {@code PLATFORM_ADMIN}; las transiciones registran auditoría de plataforma.
 */
@RestController
@RequestMapping("/api/v1/platform/tenants")
@Tag(name = "Platform - Tenants")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformTenantController {

    private final ListTenantsUseCase listTenantsUseCase;
    private final GetTenantUseCase getTenantUseCase;
    private final ChangeTenantLifecycleUseCase changeTenantLifecycleUseCase;
    private final PlatformTenantRestMapper mapper;

    public PlatformTenantController(
            ListTenantsUseCase listTenantsUseCase,
            GetTenantUseCase getTenantUseCase,
            ChangeTenantLifecycleUseCase changeTenantLifecycleUseCase,
            PlatformTenantRestMapper mapper) {
        this.listTenantsUseCase = listTenantsUseCase;
        this.getTenantUseCase = getTenantUseCase;
        this.changeTenantLifecycleUseCase = changeTenantLifecycleUseCase;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Lista los tenants, con filtro opcional por estado")
    public PagedPlatformTenantsResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status) {
        PageQuery pageQuery = PageQuery.of(page, size);
        TenantStatus statusFilter = status != null ? TenantStatus.valueOf(status) : null;
        return mapper.toPagedResponse(listTenantsUseCase.list(statusFilter, pageQuery.page(), pageQuery.size()));
    }

    @GetMapping("/{tenantId}")
    @Operation(summary = "Consulta el detalle de un tenant")
    public PlatformTenantDetailResponse get(@PathVariable UUID tenantId) {
        return mapper.toDetail(getTenantUseCase.get(tenantId));
    }

    @PostMapping("/{tenantId}/activate")
    @Operation(summary = "Activa un tenant pendiente")
    public PlatformTenantDetailResponse activate(@PathVariable UUID tenantId) {
        return mapper.toDetail(changeTenantLifecycleUseCase.activate(tenantId));
    }

    @PostMapping("/{tenantId}/suspend")
    @Operation(summary = "Suspende un tenant activo con motivo obligatorio")
    public PlatformTenantDetailResponse suspend(
            @PathVariable UUID tenantId, @Valid @RequestBody SuspendTenantRequest request) {
        return mapper.toDetail(changeTenantLifecycleUseCase.suspend(tenantId, request.reason()));
    }

    @PostMapping("/{tenantId}/reactivate")
    @Operation(summary = "Reactiva un tenant suspendido")
    public PlatformTenantDetailResponse reactivate(@PathVariable UUID tenantId) {
        return mapper.toDetail(changeTenantLifecycleUseCase.reactivate(tenantId));
    }

    @PostMapping("/{tenantId}/archive")
    @Operation(summary = "Archiva un tenant de forma permanente")
    public PlatformTenantDetailResponse archive(
            @PathVariable UUID tenantId, @Valid @RequestBody(required = false) ArchiveTenantRequest request) {
        String reason = request != null ? request.reason() : null;
        return mapper.toDetail(changeTenantLifecycleUseCase.archive(tenantId, reason));
    }
}
