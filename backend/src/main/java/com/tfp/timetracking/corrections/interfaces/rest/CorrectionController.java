package com.tfp.timetracking.corrections.interfaces.rest;

import com.tfp.timetracking.corrections.application.ApproveCorrectionRequestUseCase;
import com.tfp.timetracking.corrections.application.GetCorrectionRequestUseCase;
import com.tfp.timetracking.corrections.application.ListCorrectionRequestsUseCase;
import com.tfp.timetracking.corrections.application.RejectCorrectionRequestUseCase;
import com.tfp.timetracking.corrections.application.ResolveCorrectionCommand;
import com.tfp.timetracking.corrections.domain.CorrectionRequestStatus;
import com.tfp.timetracking.shared.interfaces.rest.PageQuery;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/v1/corrections")
@Tag(name = "Corrections")
public class CorrectionController {

    private final ListCorrectionRequestsUseCase listCorrectionRequestsUseCase;
    private final GetCorrectionRequestUseCase getCorrectionRequestUseCase;
    private final ApproveCorrectionRequestUseCase approveCorrectionRequestUseCase;
    private final RejectCorrectionRequestUseCase rejectCorrectionRequestUseCase;
    private final CorrectionRestMapper correctionRestMapper;

    public CorrectionController(
            ListCorrectionRequestsUseCase listCorrectionRequestsUseCase,
            GetCorrectionRequestUseCase getCorrectionRequestUseCase,
            ApproveCorrectionRequestUseCase approveCorrectionRequestUseCase,
            RejectCorrectionRequestUseCase rejectCorrectionRequestUseCase,
            CorrectionRestMapper correctionRestMapper) {
        this.listCorrectionRequestsUseCase = listCorrectionRequestsUseCase;
        this.getCorrectionRequestUseCase = getCorrectionRequestUseCase;
        this.approveCorrectionRequestUseCase = approveCorrectionRequestUseCase;
        this.rejectCorrectionRequestUseCase = rejectCorrectionRequestUseCase;
        this.correctionRestMapper = correctionRestMapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','TENANT_ADMIN')")
    public PagedCorrectionsResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status) {
        PageQuery pageQuery = PageQuery.of(page, size);
        CorrectionRequestStatus requestStatus = status != null ? CorrectionRequestStatus.valueOf(status) : null;
        return correctionRestMapper.toPagedResponse(
                listCorrectionRequestsUseCase.list(pageQuery.page(), pageQuery.size(), requestStatus));
    }

    @GetMapping("/{correctionId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','TENANT_ADMIN')")
    public CorrectionResponse get(@PathVariable UUID correctionId) {
        return correctionRestMapper.toResponse(getCorrectionRequestUseCase.get(correctionId));
    }

    @PostMapping("/{correctionId}/approve")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public CorrectionResponse approve(@PathVariable UUID correctionId, @Valid @RequestBody CorrectionResolutionRequest request) {
        return correctionRestMapper.toResponse(
                approveCorrectionRequestUseCase.approve(new ResolveCorrectionCommand(correctionId, request.resolutionComment())));
    }

    @PostMapping("/{correctionId}/reject")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public CorrectionResponse reject(@PathVariable UUID correctionId, @Valid @RequestBody CorrectionRejectRequest request) {
        return correctionRestMapper.toResponse(
                rejectCorrectionRequestUseCase.reject(new ResolveCorrectionCommand(correctionId, request.resolutionComment())));
    }
}
