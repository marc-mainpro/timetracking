package com.tfp.timetracking.corrections.domain;

import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.Optional;
import java.util.UUID;

public interface CorrectionRequestRepository {

    CorrectionRequest save(CorrectionRequest correctionRequest);

    Optional<CorrectionRequest> findById(UUID tenantId, UUID id);

    Optional<CorrectionRequest> findPendingByWorkdayAndRequestedBy(UUID tenantId, UUID workdayId, UUID requestedBy);

    PagedResult<CorrectionRequest> findByTenant(UUID tenantId, CorrectionRequestStatus status, int page, int size);

    PagedResult<CorrectionRequest> findByRequestedBy(UUID tenantId, UUID requestedBy, CorrectionRequestStatus status, int page, int size);
}
