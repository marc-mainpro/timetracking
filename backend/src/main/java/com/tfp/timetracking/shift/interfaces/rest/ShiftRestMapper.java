package com.tfp.timetracking.shift.interfaces.rest;

import com.tfp.timetracking.shift.application.AppShiftView;
import com.tfp.timetracking.shift.application.AssignShiftCommand;
import com.tfp.timetracking.shift.application.SaveShiftTemplateCommand;
import com.tfp.timetracking.shift.domain.model.ShiftAssignment;
import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ShiftRestMapper {

    public SaveShiftTemplateCommand toCommand(ShiftTemplateRequest request) {
        return new SaveShiftTemplateCommand(request.name(), request.startTime(), request.endTime(), request.plannedBreakMinutes());
    }

    public AssignShiftCommand toCommand(AssignShiftRequest request) {
        return new AssignShiftCommand(request.employeeId(), request.shiftTemplateId(), request.validFrom(), request.validTo());
    }

    public ShiftTemplateResponse toResponse(ShiftTemplate template) {
        return new ShiftTemplateResponse(
                template.id(),
                template.name(),
                template.startTime(),
                template.endTime(),
                Math.toIntExact(template.breakPolicy().plannedBreakDuration().toMinutes()),
                template.status().name(),
                template.crossesMidnight());
    }

    public ShiftAssignmentResponse toResponse(ShiftAssignment assignment) {
        return new ShiftAssignmentResponse(
                assignment.id(), assignment.employeeId(), assignment.shiftTemplateId(), assignment.validFrom(), assignment.validTo());
    }

    public AppShiftResponse toResponse(AppShiftView shift) {
        return new AppShiftResponse(
                shift.assignmentId(),
                shift.shiftTemplateId(),
                shift.name(),
                shift.startTime(),
                shift.endTime(),
                shift.crossesMidnight(),
                shift.plannedDuration().toString(),
                shift.plannedBreakDuration().toString(),
                shift.validFrom(),
                shift.validTo());
    }

    public List<ShiftTemplateResponse> toTemplateResponse(List<ShiftTemplate> templates) {
        return templates.stream().map(this::toResponse).toList();
    }

    public List<AppShiftResponse> toAppResponse(List<AppShiftView> shifts) {
        return shifts.stream().map(this::toResponse).toList();
    }
}
