package com.tfp.timetracking.audit.interfaces.rest;

import com.tfp.timetracking.audit.application.ListAuditEventsUseCase;
import com.tfp.timetracking.shared.interfaces.rest.PageQuery;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de auditoría de plataforma (RF-AUD-001, T130-03/05). Reutiliza
 * {@link ListAuditEventsUseCase}, que filtra por el tenant del contexto: para
 * un {@code PLATFORM_ADMIN} ese es el tenant de sistema, cuyos eventos son
 * precisamente las acciones de plataforma (activación, suspensión, etc.).
 */
@RestController
@RequestMapping("/api/v1/platform/audit")
@Tag(name = "Platform - Audit")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAuditController {

    private final ListAuditEventsUseCase listAuditEventsUseCase;
    private final AuditEventRestMapper auditEventRestMapper;

    public PlatformAuditController(
            ListAuditEventsUseCase listAuditEventsUseCase, AuditEventRestMapper auditEventRestMapper) {
        this.listAuditEventsUseCase = listAuditEventsUseCase;
        this.auditEventRestMapper = auditEventRestMapper;
    }

    @GetMapping
    public PagedAuditEventsResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        PageQuery pageQuery = PageQuery.of(page, size);
        return auditEventRestMapper.toPagedResponse(
                listAuditEventsUseCase.list(pageQuery.page(), pageQuery.size(), action, from, to));
    }
}
