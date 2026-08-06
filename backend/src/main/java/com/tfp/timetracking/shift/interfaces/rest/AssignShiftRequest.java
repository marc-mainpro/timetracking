package com.tfp.timetracking.shift.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record AssignShiftRequest(
        @NotNull UUID employeeId,
        @NotNull UUID shiftTemplateId,
        @NotNull LocalDate validFrom,
        LocalDate validTo) {}
