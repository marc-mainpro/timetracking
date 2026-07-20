package com.tfp.timetracking.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.reporting.domain.TenantEmployeeSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExportTimeSummaryCsvUseCaseTest {

    private final GenerateTenantTimeSummaryUseCase generateTenantTimeSummaryUseCase = mock(GenerateTenantTimeSummaryUseCase.class);
    private final ExportTimeSummaryCsvUseCase useCase = new ExportTimeSummaryCsvUseCase(generateTenantTimeSummaryUseCase);

    @Test
    void exportsTheSameDataAsTheTenantSummaryAsCsv() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T00:00:00Z");
        UUID employeeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(generateTenantTimeSummaryUseCase.generate(from, to))
                .thenReturn(List.of(new TenantEmployeeSummary(employeeId, Duration.ofHours(1), Duration.ZERO, 1, 0, 0)));

        String csv = useCase.export(from, to);

        assertThat(csv).startsWith("employeeId,workedSeconds,pausedSeconds,workdayCount,adjustedWorkdayCount,openWorkdays\r\n");
        assertThat(csv).contains(employeeId + ",3600,0,1,0,0");
    }
}
