package com.tfp.timetracking.corrections.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CorrectionRejectRequest(@NotBlank @Size(max = 500) String resolutionComment) {}
