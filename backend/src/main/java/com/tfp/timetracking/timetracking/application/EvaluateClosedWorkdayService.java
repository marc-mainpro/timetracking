package com.tfp.timetracking.timetracking.application;

import com.tfp.timetracking.absence.domain.AbsenceRequestRepository;
import com.tfp.timetracking.calendar.application.usecase.ResolveEffectiveCalendarUseCase;
import com.tfp.timetracking.calendar.domain.model.EffectiveCalendar;
import com.tfp.timetracking.shift.application.ResolvePlannedShiftUseCase;
import com.tfp.timetracking.timetracking.domain.HourlyRules;
import com.tfp.timetracking.timetracking.domain.HourlyRulesRepository;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayEvaluation;
import com.tfp.timetracking.timetracking.domain.WorkdayEvaluationRepository;
import com.tfp.timetracking.timetracking.domain.service.WorkdayEvaluationEngine;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluateClosedWorkdayService {

    private final ResolveEffectiveCalendarUseCase resolveEffectiveCalendarUseCase;
    private final AbsenceRequestRepository absenceRequestRepository;
    private final ResolvePlannedShiftUseCase resolvePlannedShiftUseCase;
    private final HourlyRulesRepository hourlyRulesRepository;
    private final WorkdayEvaluationRepository workdayEvaluationRepository;
    private final WorkdayEvaluationEngine engine = new WorkdayEvaluationEngine();

    public EvaluateClosedWorkdayService(
            ResolveEffectiveCalendarUseCase resolveEffectiveCalendarUseCase,
            AbsenceRequestRepository absenceRequestRepository,
            ResolvePlannedShiftUseCase resolvePlannedShiftUseCase,
            HourlyRulesRepository hourlyRulesRepository,
            WorkdayEvaluationRepository workdayEvaluationRepository) {
        this.resolveEffectiveCalendarUseCase = resolveEffectiveCalendarUseCase;
        this.absenceRequestRepository = absenceRequestRepository;
        this.resolvePlannedShiftUseCase = resolvePlannedShiftUseCase;
        this.hourlyRulesRepository = hourlyRulesRepository;
        this.workdayEvaluationRepository = workdayEvaluationRepository;
    }

    @Transactional
    public List<Object> evaluate(Workday workday) {
        LocalDate localDate = LocalDate.ofInstant(workday.startedAt(), java.time.ZoneOffset.UTC);
        EffectiveCalendar effectiveCalendar = resolveEffectiveCalendarUseCase
                .resolve(workday.employeeId(), null, localDate)
                .orElse(null);
        boolean onApprovedAbsence = !absenceRequestRepository
                .findApprovedByEmployeeAndDateRange(workday.tenantId(), workday.employeeId(), localDate, localDate)
                .isEmpty();
        // Una ausencia aprobada gana tanto al calendario como al turno: si el
        // empleado tenia el dia libre no habia nada previsto, aunque figure una
        // asignacion de turno vigente para esa fecha (T80-06, T90-06).
        Duration plannedShiftWorkDuration = null;
        if (onApprovedAbsence) {
            effectiveCalendar = null;
        } else {
            plannedShiftWorkDuration = resolvePlannedShiftUseCase
                    .resolveExpectedWorkDuration(workday.tenantId(), workday.employeeId(), localDate)
                    .orElse(null);
        }
        HourlyRules hourlyRules = hourlyRulesRepository.findByTenantId(workday.tenantId())
                .orElse(HourlyRules.withoutLimits(workday.tenantId()));
        WorkdayEvaluation rawEvaluation =
                engine.evaluate(workday, effectiveCalendar, hourlyRules, plannedShiftWorkDuration);
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
