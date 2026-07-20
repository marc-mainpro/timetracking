package com.tfp.timetracking.reporting.interfaces.rest;

import com.tfp.timetracking.reporting.application.ExportTimeSummaryCsvUseCase;
import com.tfp.timetracking.reporting.application.GenerateEmployeeTimeSummaryUseCase;
import com.tfp.timetracking.reporting.application.GenerateTenantTimeSummaryUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Informes de tiempo trabajado (T801, CONTEXT-API §2).
 */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
public class ReportController {

    private static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final GenerateEmployeeTimeSummaryUseCase generateEmployeeTimeSummaryUseCase;
    private final GenerateTenantTimeSummaryUseCase generateTenantTimeSummaryUseCase;
    private final ExportTimeSummaryCsvUseCase exportTimeSummaryCsvUseCase;
    private final ReportRestMapper reportRestMapper;

    public ReportController(
            GenerateEmployeeTimeSummaryUseCase generateEmployeeTimeSummaryUseCase,
            GenerateTenantTimeSummaryUseCase generateTenantTimeSummaryUseCase,
            ExportTimeSummaryCsvUseCase exportTimeSummaryCsvUseCase,
            ReportRestMapper reportRestMapper) {
        this.generateEmployeeTimeSummaryUseCase = generateEmployeeTimeSummaryUseCase;
        this.generateTenantTimeSummaryUseCase = generateTenantTimeSummaryUseCase;
        this.exportTimeSummaryCsvUseCase = exportTimeSummaryCsvUseCase;
        this.reportRestMapper = reportRestMapper;
    }

    @GetMapping("/employees/{employeeId}/summary")
    @PreAuthorize("hasAnyRole('EMPLOYEE','TENANT_ADMIN')")
    public List<EmployeeDaySummaryResponse> employeeSummary(
            @PathVariable UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportRestMapper.toEmployeeResponse(generateEmployeeTimeSummaryUseCase.generate(employeeId, from, to));
    }

    @GetMapping("/tenant/summary")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public List<TenantEmployeeSummaryResponse> tenantSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportRestMapper.toTenantResponse(generateTenantTimeSummaryUseCase.generate(from, to));
    }

    @GetMapping("/tenant/export.csv")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<String> tenantSummaryCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        String csv = exportTimeSummaryCsvUseCase.export(from, to);
        return ResponseEntity.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tenant-summary.csv\"")
                .body(csv);
    }
}
