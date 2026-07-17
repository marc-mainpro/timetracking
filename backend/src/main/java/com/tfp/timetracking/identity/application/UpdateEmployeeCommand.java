package com.tfp.timetracking.identity.application;

import java.util.UUID;

public record UpdateEmployeeCommand(UUID employeeId, String firstName, String lastName) {}
