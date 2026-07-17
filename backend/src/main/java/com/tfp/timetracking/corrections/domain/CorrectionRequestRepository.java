package com.tfp.timetracking.corrections.domain;

import java.util.Optional;
import java.util.UUID;

public interface CorrectionRequestRepository {

    CorrectionRequest save(CorrectionRequest correctionRequest);

    Optional<CorrectionRequest> findById(UUID tenantId, UUID id);

    Optional<CorrectionRequest> findPendingByWorkdayAndRequestedBy(UUID tenantId, UUID workdayId, UUID requestedBy);
}
