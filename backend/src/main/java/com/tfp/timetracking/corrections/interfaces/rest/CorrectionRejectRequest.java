package com.tfp.timetracking.corrections.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

public record CorrectionRejectRequest(@NotBlank String resolutionComment) {}
