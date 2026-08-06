package com.tfp.timetracking.absence.interfaces.rest;

import com.tfp.timetracking.absence.application.RequestAbsenceCommand;
import com.tfp.timetracking.absence.domain.AbsenceRequest;
import com.tfp.timetracking.absence.domain.AbsenceType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AbsenceRestMapper {

    public RequestAbsenceCommand toCommand(AbsenceRequestBody request) {
        return new RequestAbsenceCommand(request.absenceTypeId(), request.startDate(), request.endDate(), request.reason());
    }

    public AbsenceResponse toResponse(AbsenceRequest request) {
        return new AbsenceResponse(
                request.id(),
                request.employeeId(),
                request.absenceTypeId(),
                request.startDate(),
                request.endDate(),
                request.reason(),
                request.status().name(),
                request.resolvedBy(),
                request.resolvedAt(),
                request.resolutionComment(),
                request.createdAt());
    }

    public List<AbsenceResponse> toResponse(List<AbsenceRequest> requests) {
        return requests.stream().map(this::toResponse).toList();
    }

    public AbsenceTypeResponse toTypeResponse(AbsenceType type) {
        return new AbsenceTypeResponse(
                type.id(), type.code(), type.name(), type.requiresApproval(), type.allowsAttachment(), type.active());
    }

    public List<AbsenceTypeResponse> toTypeResponse(List<AbsenceType> types) {
        return types.stream().map(this::toTypeResponse).toList();
    }
}
