package com.tfp.timetracking.absence.application;

import java.time.LocalDate;
import java.util.UUID;

public record RequestAbsenceCommand(UUID absenceTypeId, LocalDate startDate, LocalDate endDate, String reason) {}
