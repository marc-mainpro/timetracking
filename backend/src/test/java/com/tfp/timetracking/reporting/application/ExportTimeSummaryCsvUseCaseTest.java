package com.tfp.timetracking.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.reporting.domain.EmployeeDirectoryPort;
import com.tfp.timetracking.reporting.domain.EmployeeName;
import com.tfp.timetracking.reporting.domain.TenantEmployeeSummary;
import com.tfp.timetracking.shared.application.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExportTimeSummaryCsvUseCaseTest {

    private final GenerateTenantTimeSummaryUseCase generateTenantTimeSummaryUseCase = mock(GenerateTenantTimeSummaryUseCase.class);
    private final EmployeeDirectoryPort employeeDirectoryPort = mock(EmployeeDirectoryPort.class);
    private final TenantContext tenantContext = mock(TenantContext.class);
    private final ExportTimeSummaryCsvUseCase useCase =
            new ExportTimeSummaryCsvUseCase(generateTenantTimeSummaryUseCase, employeeDirectoryPort, tenantContext);

    @Test
    void exportsTheSameDataAsTheTenantSummaryAsCsv() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T00:00:00Z");
        UUID tenantId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID employeeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(generateTenantTimeSummaryUseCase.generate(from, to))
                .thenReturn(List.of(new TenantEmployeeSummary(
                        employeeId,
                        Duration.ofHours(1),
                        Duration.ZERO,
                        Duration.ZERO,
                        Duration.ZERO,
                        Duration.ZERO,
                        Duration.ZERO,
                        1,
                        0,
                        0,
                        0)));
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(employeeDirectoryPort.namesByTenant(tenantId))
                .thenReturn(Map.of(employeeId, new EmployeeName("Ana", "Ruiz")));

        String csv = useCase.export(from, to);

        assertThat(csv).startsWith(
                "lastName,firstName,workedSeconds,pausedSeconds,expectedSeconds,effectiveWorkedSeconds,overtimeSeconds,deviationSeconds,workdayCount,adjustedWorkdayCount,openWorkdays,evaluatedWorkdayCount\r\n");
        assertThat(csv).contains("Ruiz,Ana,3600,0,0,0,0,0,1,0,0,0");
    }
}
