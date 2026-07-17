package com.tfp.timetracking.corrections.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.corrections.domain.CorrectionRequestStatus;
import com.tfp.timetracking.corrections.domain.ProposedChanges;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetCorrectionRequestUseCaseTest {

    @Test
    void employeeCannotSeeForeignCorrection() {
        CorrectionRequestRepository repository = org.mockito.Mockito.mock(CorrectionRequestRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        GetCorrectionRequestUseCase useCase = new GetCorrectionRequestUseCase(repository, tenantContext);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CorrectionRequest correction = CorrectionRequest.reconstitute(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), UUID.randomUUID(), "reason", validChanges(),
                CorrectionRequestStatus.PENDING, null, null, null, 0L, Instant.now());
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(userId);
        when(tenantContext.currentRoles()).thenReturn(java.util.Set.of("EMPLOYEE"));
        when(repository.findById(tenantId, correction.id())).thenReturn(java.util.Optional.of(correction));

        assertThatThrownBy(() -> useCase.get(correction.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    private ProposedChanges validChanges() {
        return new ProposedChanges(
                Instant.parse("2026-01-15T09:05:00Z"),
                Instant.parse("2026-01-15T18:05:00Z"),
                List.of(new ProposedChanges.ProposedBreak(
                        Instant.parse("2026-01-15T12:00:00Z"), Instant.parse("2026-01-15T12:30:00Z"))));
    }
}
