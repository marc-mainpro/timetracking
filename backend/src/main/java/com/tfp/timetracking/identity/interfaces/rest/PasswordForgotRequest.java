package com.tfp.timetracking.identity.interfaces.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordForgotRequest(@NotBlank @Email String email) {}
