package com.tfp.timetracking.identity.interfaces.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateEmployeeRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 10) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotEmpty Set<String> roles) {}
