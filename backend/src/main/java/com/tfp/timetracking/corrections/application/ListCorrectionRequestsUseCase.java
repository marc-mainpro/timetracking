package com.tfp.timetracking.corrections.application;

import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.corrections.domain.CorrectionRequestStatus;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.PagedResult;
import org.springframework.stereotype.Service;

@Service
public class ListCorrectionRequestsUseCase {

    private final CorrectionRequestRepository correctionRequestRepository;
    private final TenantContext tenantContext;

    public ListCorrectionRequestsUseCase(CorrectionRequestRepository correctionRequestRepository, TenantContext tenantContext) {
        this.correctionRequestRepository = correctionRequestRepository;
        this.tenantContext = tenantContext;
    }

    public PagedResult<CorrectionRequest> list(int page, int size, CorrectionRequestStatus status) {
        return tenantContext.currentRoles().contains("TENANT_ADMIN")
                ? correctionRequestRepository.findByTenant(tenantContext.currentTenantId(), status, page, size)
                : correctionRequestRepository.findByRequestedBy(
                        tenantContext.currentTenantId(), tenantContext.currentUserId(), status, page, size);
    }
}
