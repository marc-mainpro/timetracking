package com.tfp.timetracking.identity.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

public record UpdateEmployeeRequest(@NotBlank String firstName, @NotBlank String lastName) {}
