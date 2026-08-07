package com.tfp.timetracking.shift.interfaces.rest;

import java.time.LocalDate;
import java.util.UUID;

public record ShiftAssignmentResponse(
        UUID id,
        UUID employeeId,
        UUID shiftTemplateId,
        LocalDate validFrom,
        LocalDate validTo) {}
