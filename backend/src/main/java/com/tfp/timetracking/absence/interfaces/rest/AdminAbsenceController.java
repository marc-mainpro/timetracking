package com.tfp.timetracking.absence.interfaces.rest;

import com.tfp.timetracking.absence.application.ApproveAbsenceRequestUseCase;
import com.tfp.timetracking.absence.application.ListTenantAbsenceRequestsUseCase;
import com.tfp.timetracking.absence.application.RejectAbsenceRequestUseCase;
import com.tfp.timetracking.absence.application.ResolveAbsenceCommand;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/absences")
@Tag(name = "Admin - Absences")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class AdminAbsenceController {

    private final ListTenantAbsenceRequestsUseCase listTenantAbsenceRequestsUseCase;
    private final ApproveAbsenceRequestUseCase approveAbsenceRequestUseCase;
    private final RejectAbsenceRequestUseCase rejectAbsenceRequestUseCase;
    private final AbsenceRestMapper mapper;

    public AdminAbsenceController(
            ListTenantAbsenceRequestsUseCase listTenantAbsenceRequestsUseCase,
            ApproveAbsenceRequestUseCase approveAbsenceRequestUseCase,
            RejectAbsenceRequestUseCase rejectAbsenceRequestUseCase,
            AbsenceRestMapper mapper) {
        this.listTenantAbsenceRequestsUseCase = listTenantAbsenceRequestsUseCase;
        this.approveAbsenceRequestUseCase = approveAbsenceRequestUseCase;
        this.rejectAbsenceRequestUseCase = rejectAbsenceRequestUseCase;
        this.mapper = mapper;
    }

    @GetMapping
    public List<AbsenceResponse> list(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return mapper.toResponse(listTenantAbsenceRequestsUseCase.list(from, to));
    }

    @PostMapping("/{absenceId}/approve")
    public AbsenceResponse approve(@PathVariable UUID absenceId, @Valid @RequestBody AbsenceResolutionRequest request) {
        return mapper.toResponse(
                approveAbsenceRequestUseCase.approve(new ResolveAbsenceCommand(absenceId, request.resolutionComment())));
    }

    @PostMapping("/{absenceId}/reject")
    public AbsenceResponse reject(@PathVariable UUID absenceId, @Valid @RequestBody AbsenceResolutionRequest request) {
        return mapper.toResponse(
                rejectAbsenceRequestUseCase.reject(new ResolveAbsenceCommand(absenceId, request.resolutionComment())));
    }
}
