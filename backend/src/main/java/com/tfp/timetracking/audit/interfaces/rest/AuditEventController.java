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
