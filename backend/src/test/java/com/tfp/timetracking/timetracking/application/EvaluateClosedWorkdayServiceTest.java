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
import com.tfp.timetracking.shift.application.ResolvePlannedShiftUseCase;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import com.tfp.timetracking.tenant.domain.TenantStatus;
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

    private static final LocalDate DATE = LocalDate.of(2026, 8, 10);

    private final ResolveEffectiveCalendarUseCase resolveEffectiveCalendarUseCase =
            org.mockito.Mockito.mock(ResolveEffectiveCalendarUseCase.class);
    private final AbsenceRequestRepository absenceRequestRepository =
            org.mockito.Mockito.mock(AbsenceRequestRepository.class);
    private final ResolvePlannedShiftUseCase resolvePlannedShiftUseCase =
            org.mockito.Mockito.mock(ResolvePlannedShiftUseCase.class);
    private final HourlyRulesRepository hourlyRulesRepository =
            org.mockito.Mockito.mock(HourlyRulesRepository.class);
    private final WorkdayEvaluationRepository evaluationRepository =
            org.mockito.Mockito.mock(WorkdayEvaluationRepository.class);
    private final TenantRepository tenantRepository =
            org.mockito.Mockito.mock(TenantRepository.class);

    private final EvaluateClosedWorkdayService service = new EvaluateClosedWorkdayService(
            resolveEffectiveCalendarUseCase,
            absenceRequestRepository,
            resolvePlannedShiftUseCase,
            hourlyRulesRepository,
            evaluationRepository,
            tenantRepository);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @Test
    void approvedAbsenceSetsExpectedDurationToZero() {
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

        givenCalendar();
        givenApprovedAbsence();
        givenNoShift();
        givenDefaults();

        service.evaluate(workday);

        assertThat(savedEvaluation().expectedDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    void resolvesTheCivilDateUsingTheTenantTimezone() {
        Workday workday = Workday.reconstitute(
                UUID.randomUUID(),
                tenantId,
                employeeId,
                com.tfp.timetracking.timetracking.domain.WorkdayStatus.CLOSED,
                Instant.parse("2026-08-09T22:30:00Z"),
                Instant.parse("2026-08-10T06:30:00Z"),
                0L,
                Instant.parse("2026-08-09T22:30:00Z"),
                Instant.parse("2026-08-10T06:30:00Z"),
                List.of());
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant("Europe/Madrid")));
        givenCalendar();
        givenNoAbsence();
        givenNoShift();
        givenDefaults();

        service.evaluate(workday);

        verify(resolveEffectiveCalendarUseCase).resolve(employeeId, null, DATE);
        verify(absenceRequestRepository).findApprovedByEmployeeAndDateRange(tenantId, employeeId, DATE, DATE);
        verify(resolvePlannedShiftUseCase).resolveExpectedWorkDuration(tenantId, employeeId, DATE);
    }

    @Test
    void assignedShiftTakesPrecedenceOverTheCalendar() {
        // El turno es la planificacion mas especifica del empleado para ese dia;
        // el calendario solo describe la jornada tipica de su ambito (T90-06).
        Workday workday = closedWorkday();
        givenCalendar();
        givenNoAbsence();
        when(resolvePlannedShiftUseCase.resolveExpectedWorkDuration(tenantId, employeeId, DATE))
                .thenReturn(Optional.of(Duration.ofHours(6)));
        givenDefaults();

        service.evaluate(workday);

        assertThat(savedEvaluation().expectedDuration()).isEqualTo(Duration.ofHours(6));
    }

    @Test
    void fallsBackToTheCalendarWhenThereIsNoShift() {
        Workday workday = closedWorkday();
        givenCalendar();
        givenNoAbsence();
        givenNoShift();
        givenDefaults();

        service.evaluate(workday);

        assertThat(savedEvaluation().expectedDuration()).isEqualTo(Duration.ofMinutes(480));
    }

    @Test
    void approvedAbsenceBeatsAnAssignedShift() {
        // Si el empleado tenia el dia libre no habia nada previsto, aunque
        // figure una asignacion de turno vigente para esa fecha.
        Workday workday = closedWorkday();
        givenCalendar();
        givenApprovedAbsence();
        givenDefaults();

        service.evaluate(workday);

        assertThat(savedEvaluation().expectedDuration()).isEqualTo(Duration.ZERO);
        org.mockito.Mockito.verifyNoInteractions(resolvePlannedShiftUseCase);
    }

    private Workday closedWorkday() {
        return Workday.reconstitute(
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
    }

    private void givenCalendar() {
        when(resolveEffectiveCalendarUseCase.resolve(employeeId, null, DATE))
                .thenReturn(Optional.of(effectiveCalendar(tenantId, employeeId)));
    }

    private void givenApprovedAbsence() {
        when(absenceRequestRepository.findApprovedByEmployeeAndDateRange(tenantId, employeeId, DATE, DATE))
                .thenReturn(List.of(approvedAbsence(tenantId, employeeId)));
    }

    private void givenNoAbsence() {
        when(absenceRequestRepository.findApprovedByEmployeeAndDateRange(tenantId, employeeId, DATE, DATE))
                .thenReturn(List.of());
    }

    private void givenNoShift() {
        when(resolvePlannedShiftUseCase.resolveExpectedWorkDuration(tenantId, employeeId, DATE))
                .thenReturn(Optional.empty());
    }

    private void givenDefaults() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant("Europe/Madrid")));
        when(hourlyRulesRepository.findByTenantId(tenantId)).thenReturn(Optional.of(HourlyRules.withoutLimits(tenantId)));
        when(evaluationRepository.save(any(WorkdayEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Tenant tenant(String timezone) {
        return Tenant.reconstitute(
                tenantId,
                "Tenant",
                TenantStatus.ACTIVE,
                timezone,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                null);
    }

    private WorkdayEvaluation savedEvaluation() {
        ArgumentCaptor<WorkdayEvaluation> captor = ArgumentCaptor.forClass(WorkdayEvaluation.class);
        verify(evaluationRepository).save(captor.capture());
        return captor.getValue();
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
