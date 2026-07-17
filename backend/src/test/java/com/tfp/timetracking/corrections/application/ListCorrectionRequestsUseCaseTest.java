package com.tfp.timetracking.corrections.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.corrections.domain.CorrectionRequestStatus;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListCorrectionRequestsUseCaseTest {

    @Test
    void employeeSeesOnlyOwnCorrections() {
        CorrectionRequestRepository repository = org.mockito.Mockito.mock(CorrectionRequestRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        ListCorrectionRequestsUseCase useCase = new ListCorrectionRequestsUseCase(repository, tenantContext);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(userId);
        when(tenantContext.currentRoles()).thenReturn(java.util.Set.of("EMPLOYEE"));
        when(repository.findByRequestedBy(tenantId, userId, CorrectionRequestStatus.PENDING, 0, 20))
                .thenReturn(new PagedResult<>(List.of(), 0, 20, 0, 0));

        useCase.list(0, 20, CorrectionRequestStatus.PENDING);

        verify(repository).findByRequestedBy(tenantId, userId, CorrectionRequestStatus.PENDING, 0, 20);
    }

    @Test
    void adminSeesWholeTenant() {
        CorrectionRequestRepository repository = org.mockito.Mockito.mock(CorrectionRequestRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        ListCorrectionRequestsUseCase useCase = new ListCorrectionRequestsUseCase(repository, tenantContext);
        UUID tenantId = UUID.randomUUID();
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(tenantContext.currentRoles()).thenReturn(java.util.Set.of("TENANT_ADMIN"));
        when(repository.findByTenant(tenantId, null, 0, 20)).thenReturn(new PagedResult<>(List.of(), 0, 20, 0, 0));

        useCase.list(0, 20, null);

        verify(repository).findByTenant(tenantId, null, 0, 20);
    }
}
