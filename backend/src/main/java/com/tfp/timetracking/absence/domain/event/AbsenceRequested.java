package com.tfp.timetracking.absence.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AbsenceRequested(
        UUID eventId,
        Instant occurredAt,
        UUID tenantId,
        UUID aggregateId,
        UUID employeeId,
        UUID absenceTypeId,
        LocalDate startDate,
        LocalDate endDate) {}
