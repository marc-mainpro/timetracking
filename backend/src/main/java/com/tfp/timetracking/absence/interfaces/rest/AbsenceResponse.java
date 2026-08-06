package com.tfp.timetracking.absence.interfaces.rest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AbsenceResponse(
        UUID id,
        UUID employeeId,
        UUID absenceTypeId,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        String status,
        UUID resolvedBy,
        Instant resolvedAt,
        String resolutionComment,
        Instant createdAt) {}
