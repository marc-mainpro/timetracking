package com.tfp.timetracking.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shift.domain.model.ShiftAssignment;
import com.tfp.timetracking.shift.domain.model.ShiftAssignmentRepository;
import com.tfp.timetracking.shift.domain.model.ShiftBreakPolicy;
import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateRepository;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateStatus;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShiftAssignmentUseCasesTest {

    @Test
    void assignsShiftAndListsEffectiveAssignments() {
        ShiftAssignmentRepository assignmentRepository = org.mockito.Mockito.mock(ShiftAssignmentRepository.class);
        ShiftTemplateRepository templateRepository = org.mockito.Mockito.mock(ShiftTemplateRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);

        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        ShiftTemplate template = ShiftTemplate.reconstitute(
                templateId,
                tenantId,
                "General",
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                new ShiftBreakPolicy(Duration.ofMinutes(30)),
                ShiftTemplateStatus.ACTIVE);

        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(employeeId);
        when(templateRepository.findById(tenantId, templateId)).thenReturn(Optional.of(template));
        when(assignmentRepository.save(any(ShiftAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftAssignment saved = new AssignShiftUseCase(assignmentRepository, templateRepository, tenantContext)
                .assign(new AssignShiftCommand(employeeId, templateId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));

        assertThat(saved.employeeId()).isEqualTo(employeeId);
        verify(assignmentRepository).save(any(ShiftAssignment.class));

        when(assignmentRepository.findEffectiveByEmployee(tenantId, employeeId, LocalDate.of(2026, 9, 15))).thenReturn(List.of(saved));
        assertThat(new ListEmployeeShiftAssignmentsUseCase(assignmentRepository, tenantContext)
                        .listOwnEffective(LocalDate.of(2026, 9, 15)))
                .hasSize(1);
    }
}
