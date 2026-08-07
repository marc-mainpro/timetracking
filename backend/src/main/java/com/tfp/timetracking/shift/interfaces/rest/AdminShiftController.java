package com.tfp.timetracking.shift.interfaces.rest;

import com.tfp.timetracking.shift.application.AssignShiftUseCase;
import com.tfp.timetracking.shift.application.CreateShiftTemplateUseCase;
import com.tfp.timetracking.shift.application.ListShiftTemplatesUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shifts")
@Tag(name = "Admin - Shifts")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class AdminShiftController {

    private final ListShiftTemplatesUseCase listShiftTemplatesUseCase;
    private final CreateShiftTemplateUseCase createShiftTemplateUseCase;
    private final AssignShiftUseCase assignShiftUseCase;
    private final ShiftRestMapper mapper;

    public AdminShiftController(
            ListShiftTemplatesUseCase listShiftTemplatesUseCase,
            CreateShiftTemplateUseCase createShiftTemplateUseCase,
            AssignShiftUseCase assignShiftUseCase,
            ShiftRestMapper mapper) {
        this.listShiftTemplatesUseCase = listShiftTemplatesUseCase;
        this.createShiftTemplateUseCase = createShiftTemplateUseCase;
        this.assignShiftUseCase = assignShiftUseCase;
        this.mapper = mapper;
    }

    @GetMapping("/templates")
    public List<ShiftTemplateResponse> listTemplates() {
        return mapper.toTemplateResponse(listShiftTemplatesUseCase.list());
    }

    @PostMapping("/templates")
    public ShiftTemplateResponse createTemplate(@Valid @RequestBody ShiftTemplateRequest request) {
        return mapper.toResponse(createShiftTemplateUseCase.create(mapper.toCommand(request)));
    }

    @PostMapping("/assignments")
    public ShiftAssignmentResponse assign(@Valid @RequestBody AssignShiftRequest request) {
        return mapper.toResponse(assignShiftUseCase.assign(mapper.toCommand(request)));
    }
}
