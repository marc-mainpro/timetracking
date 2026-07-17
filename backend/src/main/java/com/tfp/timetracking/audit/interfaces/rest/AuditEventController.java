package com.tfp.timetracking.audit.interfaces.rest;

import com.tfp.timetracking.audit.application.ListAuditEventsUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-events")
@Tag(name = "Audit")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class AuditEventController {

    private final ListAuditEventsUseCase listAuditEventsUseCase;
    private final AuditEventRestMapper auditEventRestMapper;

    public AuditEventController(ListAuditEventsUseCase listAuditEventsUseCase, AuditEventRestMapper auditEventRestMapper) {
        this.listAuditEventsUseCase = listAuditEventsUseCase;
        this.auditEventRestMapper = auditEventRestMapper;
    }

    @GetMapping
    public PagedAuditEventsResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return auditEventRestMapper.toPagedResponse(listAuditEventsUseCase.list(page, size, action, from, to));
    }
}
