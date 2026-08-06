package com.tfp.timetracking.shift.application;

import java.time.LocalDate;
import java.util.UUID;

public record AssignShiftCommand(UUID employeeId, UUID shiftTemplateId, LocalDate validFrom, LocalDate validTo) {}
