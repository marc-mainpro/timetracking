package com.tfp.timetracking.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.shift.domain.model.ShiftAssignment;
import com.tfp.timetracking.shift.domain.model.ShiftAssignmentRepository;
import com.tfp.timetracking.shift.domain.model.ShiftBreakPolicy;
import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResolvePlannedShiftUseCaseTest {

    private final ShiftAssignmentRepository assignmentRepository = mock(ShiftAssignmentRepository.class);
    private final ShiftTemplateRepository templateRepository = mock(ShiftTemplateRepository.class);
    private final ResolvePlannedShiftUseCase useCase =
            new ResolvePlannedShiftUseCase(assignmentRepository, templateRepository);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 3, 10);

    @Test
    void discountsThePlannedBreakFromTheShiftSpan() {
        // 09:00-17:00 son 8 h de presencia, pero con 1 h de pausa prevista el
        // trabajo previsto son 7 h: la jornada real tambien se mide sin pausas.
        ShiftTemplate template = template(LocalTime.of(9, 0), LocalTime.of(17, 0), Duration.ofHours(1));
        givenAssignment(template);

        Optional<Duration> expected = useCase.resolveExpectedWorkDuration(tenantId, employeeId, date);

        assertThat(expected).contains(Duration.ofHours(7));
    }

    @Test
    void handlesShiftsCrossingMidnight() {
        // 22:00-06:00 no es fin menos inicio: son 8 h que cruzan la medianoche.
        ShiftTemplate template = template(LocalTime.of(22, 0), LocalTime.of(6, 0), Duration.ofMinutes(30));
        givenAssignment(template);

        Optional<Duration> expected = useCase.resolveExpectedWorkDuration(tenantId, employeeId, date);

        assertThat(template.crossesMidnight()).isTrue();
        assertThat(expected).contains(Duration.ofHours(7).plusMinutes(30));
    }

    @Test
    void returnsEmptyWhenTheEmployeeHasNoShiftThatDay() {
        when(assignmentRepository.findEffectiveByEmployee(tenantId, employeeId, date)).thenReturn(List.of());

        assertThat(useCase.resolveExpectedWorkDuration(tenantId, employeeId, date)).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheAssignedTemplateNoLongerExists() {
        ShiftAssignment assignment = ShiftAssignment.create(
                tenantId, employeeId, UUID.randomUUID(), date.minusDays(1), null, UUID.randomUUID());
        when(assignmentRepository.findEffectiveByEmployee(tenantId, employeeId, date)).thenReturn(List.of(assignment));
        when(templateRepository.findById(tenantId, assignment.shiftTemplateId())).thenReturn(Optional.empty());

        assertThat(useCase.resolveExpectedWorkDuration(tenantId, employeeId, date)).isEmpty();
    }

    @Test
    void picksTheMostRecentAssignmentDeterministicallyWhenSeveralAreEffective() {
        // No deberia ocurrir (las asignaciones solapadas se rechazan), pero si
        // ocurre por datos heredados el resultado no puede depender del orden
        // que devuelva la base de datos.
        ShiftTemplate oldTemplate = template(LocalTime.of(8, 0), LocalTime.of(16, 0), Duration.ZERO);
        ShiftTemplate newTemplate = template(LocalTime.of(9, 0), LocalTime.of(14, 0), Duration.ZERO);
        ShiftAssignment older = ShiftAssignment.create(
                tenantId, employeeId, oldTemplate.id(), date.minusDays(30), null, UUID.randomUUID());
        ShiftAssignment newer = ShiftAssignment.create(
                tenantId, employeeId, newTemplate.id(), date.minusDays(1), null, UUID.randomUUID());
        when(templateRepository.findById(tenantId, newTemplate.id())).thenReturn(Optional.of(newTemplate));

        when(assignmentRepository.findEffectiveByEmployee(tenantId, employeeId, date))
                .thenReturn(List.of(older, newer));
        assertThat(useCase.resolveExpectedWorkDuration(tenantId, employeeId, date)).contains(Duration.ofHours(5));

        when(assignmentRepository.findEffectiveByEmployee(tenantId, employeeId, date))
                .thenReturn(List.of(newer, older));
        assertThat(useCase.resolveExpectedWorkDuration(tenantId, employeeId, date)).contains(Duration.ofHours(5));
    }

    private ShiftTemplate template(LocalTime start, LocalTime end, Duration plannedBreak) {
        return ShiftTemplate.create(
                tenantId, "Turno", start, end, new ShiftBreakPolicy(plannedBreak), UUID.randomUUID());
    }

    private void givenAssignment(ShiftTemplate template) {
        ShiftAssignment assignment = ShiftAssignment.create(
                tenantId, employeeId, template.id(), date.minusDays(1), null, UUID.randomUUID());
        when(assignmentRepository.findEffectiveByEmployee(tenantId, employeeId, date)).thenReturn(List.of(assignment));
        when(templateRepository.findById(tenantId, template.id())).thenReturn(Optional.of(template));
    }
}
