package com.tfp.timetracking.identity.interfaces.rest;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record AssignRolesRequest(@NotEmpty Set<String> roles) {}
