package com.tfp.timetracking.timetracking.domain;

import java.util.Optional;
import java.util.UUID;

public interface WorkdayEvaluationRepository {

    WorkdayEvaluation save(WorkdayEvaluation evaluation);

    Optional<WorkdayEvaluation> findByWorkdayId(UUID tenantId, UUID workdayId);
}
