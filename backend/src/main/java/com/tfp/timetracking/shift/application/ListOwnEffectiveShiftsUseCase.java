package com.tfp.timetracking.shift.application;

import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shift.domain.model.ShiftAssignment;
import com.tfp.timetracking.shift.domain.model.ShiftAssignmentRepository;
import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListOwnEffectiveShiftsUseCase {

    private final ShiftAssignmentRepository assignmentRepository;
    private final ShiftTemplateRepository templateRepository;
    private final TenantContext tenantContext;

    public ListOwnEffectiveShiftsUseCase(
            ShiftAssignmentRepository assignmentRepository,
            ShiftTemplateRepository templateRepository,
            TenantContext tenantContext) {
        this.assignmentRepository = assignmentRepository;
        this.templateRepository = templateRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public List<AppShiftView> list(LocalDate date) {
        return assignmentRepository.findEffectiveByEmployee(tenantContext.currentTenantId(), tenantContext.currentUserId(), date).stream()
                .map(this::toView)
                .toList();
    }

    private AppShiftView toView(ShiftAssignment assignment) {
        ShiftTemplate template = templateRepository.findById(tenantContext.currentTenantId(), assignment.shiftTemplateId()).orElseThrow();
        return new AppShiftView(
                assignment.id(),
                template.id(),
                template.name(),
                template.startTime(),
                template.endTime(),
                template.crossesMidnight(),
                template.plannedDuration(),
                template.breakPolicy().plannedBreakDuration(),
                assignment.validFrom(),
                assignment.validTo());
    }
}
