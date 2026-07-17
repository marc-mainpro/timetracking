package com.tfp.timetracking.corrections.application;

import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetCorrectionRequestUseCase {

    private final CorrectionRequestRepository correctionRequestRepository;
    private final TenantContext tenantContext;

    public GetCorrectionRequestUseCase(CorrectionRequestRepository correctionRequestRepository, TenantContext tenantContext) {
        this.correctionRequestRepository = correctionRequestRepository;
        this.tenantContext = tenantContext;
    }

    public CorrectionRequest get(UUID correctionId) {
        CorrectionRequest correction = correctionRequestRepository.findById(tenantContext.currentTenantId(), correctionId)
                .orElseThrow(() -> new ResourceNotFoundException("Correccion no encontrada"));
        if (tenantContext.currentRoles().contains("TENANT_ADMIN") || correction.requestedBy().equals(tenantContext.currentUserId())) {
            return correction;
        }
        throw new ResourceNotFoundException("Correccion no encontrada");
    }
}
