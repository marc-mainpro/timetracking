package com.tfp.timetracking.reporting.interfaces.rest;

import com.tfp.timetracking.reporting.domain.EmployeeDaySummary;
import com.tfp.timetracking.reporting.domain.TenantEmployeeSummary;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReportRestMapper {

    public List<EmployeeDaySummaryResponse> toEmployeeResponse(List<EmployeeDaySummary> summaries) {
        return summaries.stream().map(this::toResponse).toList();
    }

    public List<TenantEmployeeSummaryResponse> toTenantResponse(List<TenantEmployeeSummary> summaries) {
        return summaries.stream().map(this::toResponse).toList();
    }

    private EmployeeDaySummaryResponse toResponse(EmployeeDaySummary summary) {
        return new EmployeeDaySummaryResponse(
                summary.day(), summary.worked(), summary.paused(), summary.workdayCount(), summary.adjustedWorkdayCount(), summary.openWorkdays());
    }

    private TenantEmployeeSummaryResponse toResponse(TenantEmployeeSummary summary) {
        return new TenantEmployeeSummaryResponse(
                summary.employeeId(),
                summary.worked(),
                summary.paused(),
                summary.workdayCount(),
                summary.adjustedWorkdayCount(),
                summary.openWorkdays());
    }
}
