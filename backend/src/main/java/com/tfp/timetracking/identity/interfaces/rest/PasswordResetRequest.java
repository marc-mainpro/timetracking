package com.tfp.timetracking.identity.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(@NotBlank String token, @NotBlank @Size(min = 10, max = 200) String newPassword) {}
