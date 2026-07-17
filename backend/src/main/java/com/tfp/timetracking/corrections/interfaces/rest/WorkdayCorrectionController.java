package com.tfp.timetracking.corrections.interfaces.rest;

import com.tfp.timetracking.corrections.application.RequestWorkdayCorrectionCommand;
import com.tfp.timetracking.corrections.application.RequestWorkdayCorrectionUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workdays")
@Tag(name = "Corrections")
public class WorkdayCorrectionController {

    private final RequestWorkdayCorrectionUseCase requestWorkdayCorrectionUseCase;
    private final CorrectionRestMapper correctionRestMapper;

    public WorkdayCorrectionController(
            RequestWorkdayCorrectionUseCase requestWorkdayCorrectionUseCase, CorrectionRestMapper correctionRestMapper) {
        this.requestWorkdayCorrectionUseCase = requestWorkdayCorrectionUseCase;
        this.correctionRestMapper = correctionRestMapper;
    }

    @PostMapping("/{workdayId}/corrections")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<CorrectionResponse> request(
            @PathVariable UUID workdayId, @Valid @RequestBody CorrectionRequestDto request) {
        CorrectionResponse response = correctionRestMapper.toResponse(requestWorkdayCorrectionUseCase.request(
                new RequestWorkdayCorrectionCommand(workdayId, request.reason(), correctionRestMapper.toDomain(request.proposedChanges()))));
        return ResponseEntity.created(URI.create("/api/v1/corrections/" + response.id())).body(response);
    }
}
