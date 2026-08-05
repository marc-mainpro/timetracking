package com.tfp.timetracking.timetracking.application;

import com.tfp.timetracking.calendar.application.usecase.ResolveEffectiveCalendarUseCase;
import com.tfp.timetracking.calendar.domain.model.EffectiveCalendar;
import com.tfp.timetracking.timetracking.domain.HourlyRules;
import com.tfp.timetracking.timetracking.domain.HourlyRulesRepository;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayEvaluation;
import com.tfp.timetracking.timetracking.domain.WorkdayEvaluationRepository;
import com.tfp.timetracking.timetracking.domain.service.WorkdayEvaluationEngine;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluateClosedWorkdayService {

    private final ResolveEffectiveCalendarUseCase resolveEffectiveCalendarUseCase;
    private final HourlyRulesRepository hourlyRulesRepository;
    private final WorkdayEvaluationRepository workdayEvaluationRepository;
    private final WorkdayEvaluationEngine engine = new WorkdayEvaluationEngine();

    public EvaluateClosedWorkdayService(
            ResolveEffectiveCalendarUseCase resolveEffectiveCalendarUseCase,
            HourlyRulesRepository hourlyRulesRepository,
            WorkdayEvaluationRepository workdayEvaluationRepository) {
        this.resolveEffectiveCalendarUseCase = resolveEffectiveCalendarUseCase;
        this.hourlyRulesRepository = hourlyRulesRepository;
        this.workdayEvaluationRepository = workdayEvaluationRepository;
    }

    @Transactional
    public List<Object> evaluate(Workday workday) {
        LocalDate localDate = LocalDate.ofInstant(workday.startedAt(), java.time.ZoneOffset.UTC);
        EffectiveCalendar effectiveCalendar = resolveEffectiveCalendarUseCase
                .resolve(workday.employeeId(), null, localDate)
                .orElse(null);
        HourlyRules hourlyRules = hourlyRulesRepository.findByTenantId(workday.tenantId())
                .orElse(HourlyRules.withoutLimits(workday.tenantId()));
        WorkdayEvaluation rawEvaluation = engine.evaluate(workday, effectiveCalendar, hourlyRules);
        WorkdayEvaluation evaluation = WorkdayEvaluation.create(
                rawEvaluation.workdayId(),
                rawEvaluation.tenantId(),
                rawEvaluation.employeeId(),
                rawEvaluation.expectedDuration(),
                rawEvaluation.workedDuration(),
                rawEvaluation.effectiveWorkedDuration(),
                rawEvaluation.pausedDuration(),
                rawEvaluation.overtimeDuration(),
                rawEvaluation.deviationDuration(),
                rawEvaluation.anomalies(),
                () -> java.time.Instant.now(),
                java.util.UUID::randomUUID);
        workdayEvaluationRepository.save(evaluation);
        return evaluation.pullDomainEvents();
    }
}
