package com.tfp.timetracking.absence.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record AbsenceRequestBody(
        @NotNull UUID absenceTypeId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 500) String reason) {}
