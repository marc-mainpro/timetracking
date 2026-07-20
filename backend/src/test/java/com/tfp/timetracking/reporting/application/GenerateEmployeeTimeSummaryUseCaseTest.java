package com.tfp.timetracking.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.reporting.domain.WorkdayReportEntry;
import com.tfp.timetracking.reporting.domain.WorkdaySummaryQueryPort;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import com.tfp.timetracking.tenant.domain.TenantStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenerateEmployeeTimeSummaryUseCaseTest {

    private final WorkdaySummaryQueryPort workdaySummaryQueryPort = mock(WorkdaySummaryQueryPort.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final TenantContext tenantContext = mock(TenantContext.class);
    private final GenerateEmployeeTimeSummaryUseCase useCase =
            new GenerateEmployeeTimeSummaryUseCase(workdaySummaryQueryPort, userRepository, tenantRepository, tenantContext);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final Instant from = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant to = Instant.parse("2026-01-31T00:00:00Z");

    @BeforeEach
    void commonStubs() {
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
    }

    @Test
    void employeeCanReadTheirOwnSummary() {
        when(tenantContext.currentRoles()).thenReturn(Set.of("EMPLOYEE"));
        when(tenantContext.currentUserId()).thenReturn(employeeId);
        when(userRepository.findById(tenantId, employeeId)).thenReturn(Optional.of(mock(com.tfp.timetracking.identity.domain.User.class)));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant()));
        when(workdaySummaryQueryPort.findByEmployee(tenantId, employeeId, from, to)).thenReturn(List.of());

        assertThat(useCase.generate(employeeId, from, to)).isEmpty();
    }

    @Test
    void employeeCannotReadAnotherEmployeesSummary() {
        UUID otherEmployeeId = UUID.randomUUID();
        when(tenantContext.currentRoles()).thenReturn(Set.of("EMPLOYEE"));
        when(tenantContext.currentUserId()).thenReturn(employeeId);

        assertThatThrownBy(() -> useCase.generate(otherEmployeeId, from, to)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminCanReadAnyEmployeesSummaryInTheirTenant() {
        when(tenantContext.currentRoles()).thenReturn(Set.of("TENANT_ADMIN"));
        when(tenantContext.currentUserId()).thenReturn(UUID.randomUUID());
        when(userRepository.findById(tenantId, employeeId)).thenReturn(Optional.of(mock(com.tfp.timetracking.identity.domain.User.class)));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant()));
        when(workdaySummaryQueryPort.findByEmployee(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(useCase.generate(employeeId, from, to)).isEmpty();
    }

    @Test
    void adminRequestingAnUnknownEmployeeGetsNotFound() {
        when(tenantContext.currentRoles()).thenReturn(Set.of("TENANT_ADMIN"));
        when(tenantContext.currentUserId()).thenReturn(UUID.randomUUID());
        when(userRepository.findById(tenantId, employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.generate(employeeId, from, to)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsAnInvalidDateRange() {
        when(tenantContext.currentRoles()).thenReturn(Set.of("TENANT_ADMIN"));
        when(tenantContext.currentUserId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> useCase.generate(employeeId, to, from)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesTheEmployeeWorkdaysToBuildTheReportGivenTheTenantZone() {
        when(tenantContext.currentRoles()).thenReturn(Set.of("TENANT_ADMIN"));
        when(tenantContext.currentUserId()).thenReturn(UUID.randomUUID());
        when(userRepository.findById(tenantId, employeeId)).thenReturn(Optional.of(mock(com.tfp.timetracking.identity.domain.User.class)));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant()));
        WorkdayReportEntry entry = new WorkdayReportEntry(
                UUID.randomUUID(), employeeId, false, false, Instant.parse("2026-01-05T08:00:00Z"), Instant.parse("2026-01-05T10:00:00Z"), List.of());
        when(workdaySummaryQueryPort.findByEmployee(tenantId, employeeId, from, to)).thenReturn(List.of(entry));

        assertThat(useCase.generate(employeeId, from, to)).hasSize(1);
    }

    private Tenant tenant() {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        return Tenant.reconstitute(tenantId, "Acme", TenantStatus.ACTIVE, "Europe/Madrid", now, now);
    }
}
