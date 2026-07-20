package com.tfp.timetracking.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.reporting.domain.WorkdayReportEntry;
import com.tfp.timetracking.reporting.domain.WorkdaySummaryQueryPort;
import com.tfp.timetracking.shared.application.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GenerateTenantTimeSummaryUseCaseTest {

    private final WorkdaySummaryQueryPort workdaySummaryQueryPort = mock(WorkdaySummaryQueryPort.class);
    private final TenantContext tenantContext = mock(TenantContext.class);
    private final GenerateTenantTimeSummaryUseCase useCase =
            new GenerateTenantTimeSummaryUseCase(workdaySummaryQueryPort, tenantContext);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final Instant from = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant to = Instant.parse("2026-01-31T00:00:00Z");

    @Test
    void aggregatesEntriesByEmployeeForTheCurrentTenant() {
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        WorkdayReportEntry entry = new WorkdayReportEntry(
                UUID.randomUUID(), employeeId, false, false, Instant.parse("2026-01-05T08:00:00Z"), Instant.parse("2026-01-05T10:00:00Z"), List.of());
        when(workdaySummaryQueryPort.findByTenant(tenantId, from, to)).thenReturn(List.of(entry));

        assertThat(useCase.generate(from, to)).hasSize(1).first().satisfies(summary -> assertThat(summary.employeeId()).isEqualTo(employeeId));
    }

    @Test
    void rejectsAnInvalidDateRange() {
        assertThatThrownBy(() -> useCase.generate(to, from)).isInstanceOf(IllegalArgumentException.class);
    }
}
