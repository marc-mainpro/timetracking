package com.tfp.timetracking.timetracking.interfaces.rest;

import com.tfp.timetracking.timetracking.application.GetHourlyRulesUseCase;
import com.tfp.timetracking.timetracking.application.UpdateHourlyRulesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/hourly-rules")
@Tag(name = "Admin - Hourly rules")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class AdminHourlyRulesController {

    private final GetHourlyRulesUseCase getHourlyRulesUseCase;
    private final UpdateHourlyRulesUseCase updateHourlyRulesUseCase;
    private final HourlyRulesRestMapper mapper;

    public AdminHourlyRulesController(
            GetHourlyRulesUseCase getHourlyRulesUseCase,
            UpdateHourlyRulesUseCase updateHourlyRulesUseCase,
            HourlyRulesRestMapper mapper) {
        this.getHourlyRulesUseCase = getHourlyRulesUseCase;
        this.updateHourlyRulesUseCase = updateHourlyRulesUseCase;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Consulta las reglas horarias del tenant autenticado")
    public HourlyRulesResponse get() {
        return mapper.toResponse(getHourlyRulesUseCase.get());
    }

    @PutMapping
    @Operation(summary = "Sustituye las reglas horarias del tenant autenticado")
    public HourlyRulesResponse update(@Valid @RequestBody HourlyRulesRequest request) {
        return mapper.toResponse(
                updateHourlyRulesUseCase.update(request.maxDailyWorkMinutes(), request.requiredBreakMinutes()));
    }
}
