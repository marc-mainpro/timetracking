package com.tfp.timetracking.timetracking.infrastructure.persistence;

import com.tfp.timetracking.timetracking.domain.WorkdayAnomaly;
import com.tfp.timetracking.timetracking.domain.WorkdayEvaluation;
import com.tfp.timetracking.timetracking.domain.WorkdayEvaluationRepository;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class WorkdayEvaluationRepositoryAdapter implements WorkdayEvaluationRepository {

    private final WorkdayEvaluationJpaRepository jpaRepository;

    public WorkdayEvaluationRepositoryAdapter(WorkdayEvaluationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public WorkdayEvaluation save(WorkdayEvaluation evaluation) {
        WorkdayEvaluationJpaEntity saved = jpaRepository.save(new WorkdayEvaluationJpaEntity(
                evaluation.workdayId(),
                evaluation.tenantId(),
                evaluation.employeeId(),
                evaluation.expectedDuration().toMinutes(),
                evaluation.workedDuration().toMinutes(),
                evaluation.pausedDuration().toMinutes(),
                evaluation.overtimeDuration().toMinutes(),
                encode(evaluation.anomalies()),
                evaluation.evaluatedAt()));
        return toDomain(saved);
    }

    @Override
    public Optional<WorkdayEvaluation> findByWorkdayId(UUID tenantId, UUID workdayId) {
        return jpaRepository.findByTenantIdAndWorkdayId(tenantId, workdayId).map(this::toDomain);
    }

    private WorkdayEvaluation toDomain(WorkdayEvaluationJpaEntity entity) {
        return WorkdayEvaluation.reconstitute(
                entity.getWorkdayId(),
                entity.getTenantId(),
                entity.getEmployeeId(),
                Duration.ofMinutes(entity.getExpectedMinutes()),
                Duration.ofMinutes(entity.getWorkedMinutes()),
                Duration.ofMinutes(entity.getPausedMinutes()),
                Duration.ofMinutes(entity.getOvertimeMinutes()),
                decode(entity.getAnomalies()),
                entity.getEvaluatedAt());
    }

    private String encode(List<WorkdayAnomaly> anomalies) {
        return anomalies.stream().map(Enum::name).sorted().reduce((left, right) -> left + "," + right).orElse("");
    }

    private List<WorkdayAnomaly> decode(String anomalies) {
        if (anomalies == null || anomalies.isBlank()) {
            return List.of();
        }
        return Arrays.stream(anomalies.split(",")).map(WorkdayAnomaly::valueOf).toList();
    }
}
