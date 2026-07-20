package com.tfp.timetracking.reporting.application;

import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.reporting.domain.EmployeeDaySummary;
import com.tfp.timetracking.reporting.domain.ReportDateRange;
import com.tfp.timetracking.reporting.domain.TimeSummaryCalculator;
import com.tfp.timetracking.reporting.domain.WorkdaySummaryQueryPort;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Informe diario de tiempo trabajado de un empleado (T801,
 * {@code GET /api/v1/reports/employees/{employeeId}/summary}).
 *
 * <p>Un {@code EMPLOYEE} solo puede pedir el suyo propio; un
 * {@code TENANT_ADMIN} puede pedir el de cualquier empleado de su tenant.
 * Igual que el resto del sistema (jornadas, correcciones), un empleado que
 * pide el informe de otro, o el informe de un {@code employeeId} de otro
 * tenant, recibe {@code 404} en vez de {@code 403} para no revelar
 * existencia.
 */
@Service
public class GenerateEmployeeTimeSummaryUseCase {

    private final WorkdaySummaryQueryPort workdaySummaryQueryPort;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantContext tenantContext;

    public GenerateEmployeeTimeSummaryUseCase(
            WorkdaySummaryQueryPort workdaySummaryQueryPort,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            TenantContext tenantContext) {
        this.workdaySummaryQueryPort = workdaySummaryQueryPort;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.tenantContext = tenantContext;
    }

    public List<EmployeeDaySummary> generate(UUID employeeId, Instant from, Instant to) {
        ReportDateRange range = new ReportDateRange(from, to);
        UUID tenantId = tenantContext.currentTenantId();

        boolean isAdmin = tenantContext.currentRoles().contains("TENANT_ADMIN");
        boolean isOwnSummary = employeeId.equals(tenantContext.currentUserId());
        if (!isAdmin && !isOwnSummary) {
            throw new ResourceNotFoundException("Empleado no encontrado");
        }
        userRepository.findById(tenantId, employeeId).orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado"));

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));
        ZoneId zone = ZoneId.of(tenant.timezone());

        return TimeSummaryCalculator.summarizeByDay(
                workdaySummaryQueryPort.findByEmployee(tenantId, employeeId, range.from(), range.to()), zone);
    }
}
