package com.tfp.timetracking.timetracking.interfaces.rest;

import com.tfp.timetracking.timetracking.application.GetTenantWorkdayUseCase;
import com.tfp.timetracking.timetracking.application.ListTenantWorkdaysUseCase;
import com.tfp.timetracking.shared.interfaces.rest.PageQuery;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/workdays")
@Tag(name = "Admin Workdays")
public class AdminWorkdayController {

    private final ListTenantWorkdaysUseCase listTenantWorkdaysUseCase;
    private final GetTenantWorkdayUseCase getTenantWorkdayUseCase;
    private final WorkdayRestMapper workdayRestMapper;

    public AdminWorkdayController(
            ListTenantWorkdaysUseCase listTenantWorkdaysUseCase,
            GetTenantWorkdayUseCase getTenantWorkdayUseCase,
            WorkdayRestMapper workdayRestMapper) {
        this.listTenantWorkdaysUseCase = listTenantWorkdaysUseCase;
        this.getTenantWorkdayUseCase = getTenantWorkdayUseCase;
        this.workdayRestMapper = workdayRestMapper;
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public PagedResponse<WorkdayResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        PageQuery pageQuery = PageQuery.of(page, size);
        return workdayRestMapper.toPagedResponse(
                listTenantWorkdaysUseCase.list(pageQuery.page(), pageQuery.size(), employeeId, from, to));
    }

    @GetMapping("/{workdayId}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public WorkdayResponse get(@PathVariable UUID workdayId) {
        return workdayRestMapper.toResponse(getTenantWorkdayUseCase.get(workdayId));
    }
}
