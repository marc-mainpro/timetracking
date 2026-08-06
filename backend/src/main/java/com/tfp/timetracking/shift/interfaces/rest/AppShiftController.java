package com.tfp.timetracking.shift.interfaces.rest;

import com.tfp.timetracking.shift.application.ListOwnEffectiveShiftsUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app/shifts")
@Tag(name = "Shifts")
public class AppShiftController {

    private final ListOwnEffectiveShiftsUseCase listOwnEffectiveShiftsUseCase;
    private final ShiftRestMapper mapper;

    public AppShiftController(ListOwnEffectiveShiftsUseCase listOwnEffectiveShiftsUseCase, ShiftRestMapper mapper) {
        this.listOwnEffectiveShiftsUseCase = listOwnEffectiveShiftsUseCase;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<AppShiftResponse> list(@RequestParam @NotNull LocalDate date) {
        return mapper.toAppResponse(listOwnEffectiveShiftsUseCase.list(date));
    }
}
