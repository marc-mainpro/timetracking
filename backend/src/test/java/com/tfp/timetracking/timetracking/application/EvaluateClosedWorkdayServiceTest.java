package com.tfp.timetracking.timetracking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.absence.domain.AbsenceRequest;
import com.tfp.timetracking.absence.domain.AbsenceRequestRepository;
import com.tfp.timetracking.calendar.application.usecase.ResolveEffectiveCalendarUseCase;
import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignment;
import com.tfp.timetracking.calendar.domain.model.CalendarDay;
import com.tfp.timetracking.calendar.domain.model.DaySource;
import com.tfp.timetracking.calendar.domain.model.EffectiveCalendar;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import com.tfp.timetracking.timetracking.domain.HourlyRules;
import com.tfp.timetracking.timetracking.domain.HourlyRulesRepository;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayEvaluation;
import com.tfp.timetracking.timetracking.domain.WorkdayEvaluationRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EvaluateClosedWorkdayServiceTest {

    @Test
    void approvedAbsenceSetsExpectedDurationToZero() {
        ResolveEffectiveCalendarUseCase resolveEffectiveCalendarUseCase = org.mockito.Mockito.mock(ResolveEffectiveCalendarUseCase.class);
        AbsenceRequestRepository absenceRequestRepository = org.mockito.Mockito.mock(AbsenceRequestRepository.class);
        HourlyRulesRepository hourlyRulesRepository = org.mockito.Mockito.mock(HourlyRulesRepository.class);
        WorkdayEvaluationRepository evaluationRepository = org.mockito.Mockito.mock(WorkdayEvaluationRepository.class);

        EvaluateClosedWorkdayService service = new EvaluateClosedWorkdayService(
                resolveEffectiveCalendarUseCase, absenceRequestRepository, hourlyRulesRepository, evaluationRepository);

        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Workday workday = Workday.reconstitute(
                UUID.randomUUID(),
                tenantId,
                employeeId,
                com.tfp.timetracking.timetracking.domain.WorkdayStatus.CLOSED,
                Instant.parse("2026-08-10T08:00:00Z"),
                Instant.parse("2026-08-10T16:00:00Z"),
                0L,
                Instant.parse("2026-08-10T08:00:00Z"),
                Instant.parse("2026-08-10T16:00:00Z"),
                List.of());

        when(resolveEffectiveCalendarUseCase.resolve(employeeId, null, LocalDate.of(2026, 8, 10)))
                .thenReturn(Optional.of(effectiveCalendar(tenantId, employeeId)));
        when(absenceRequestRepository.findApprovedByEmployeeAndDateRange(tenantId, employeeId, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10)))
                .thenReturn(List.of(approvedAbsence(tenantId, employeeId)));
        when(hourlyRulesRepository.findByTenantId(tenantId)).thenReturn(Optional.of(HourlyRules.withoutLimits(tenantId)));
        when(evaluationRepository.save(any(WorkdayEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.evaluate(workday);

        ArgumentCaptor<WorkdayEvaluation> captor = ArgumentCaptor.forClass(WorkdayEvaluation.class);
        verify(evaluationRepository).save(captor.capture());
        assertThat(captor.getValue().expectedDuration()).isEqualTo(Duration.ZERO);
    }

    private EffectiveCalendar effectiveCalendar(UUID tenantId, UUID employeeId) {
        CalendarAssignment assignment = CalendarAssignment.reconstitute(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), AssignmentScope.EMPLOYEE, employeeId, Instant.now(), Instant.now());
        WorkCalendar calendar = WorkCalendar.reconstitute(
                UUID.randomUUID(),
                tenantId,
                "General",
                "Europe/Madrid",
                LocalDate.of(2026, 1, 1),
                null,
                com.tfp.timetracking.calendar.domain.model.CalendarStatus.ACTIVE,
                List.of(),
                List.of(),
                List.of(),
                0L,
                Instant.now(),
                Instant.now());
        return new EffectiveCalendar(
                assignment,
                calendar,
                new CalendarDay(LocalDate.of(2026, 8, 10), true, 480, DaySource.WEEKLY_RULE));
    }

    private AbsenceRequest approvedAbsence(UUID tenantId, UUID employeeId) {
        AbsenceRequest request = AbsenceRequest.request(
                tenantId,
                employeeId,
                UUID.randomUUID(),
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 10),
                "Vacaciones",
                Instant.parse("2026-08-01T10:00:00Z"),
                UUID::randomUUID);
        request.pullDomainEvents();
        request.approve(UUID.randomUUID(), null, Instant.parse("2026-08-02T10:00:00Z"), UUID::randomUUID);
        request.pullDomainEvents();
        return request;
    }
}
