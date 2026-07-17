package com.tfp.timetracking.identity.application;

import java.util.Set;
import java.util.UUID;

public record EmployeeRolesCommand(UUID employeeId, Set<String> roles) {}
