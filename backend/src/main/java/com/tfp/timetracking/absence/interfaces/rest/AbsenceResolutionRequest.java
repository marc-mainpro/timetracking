package com.tfp.timetracking.absence.interfaces.rest;

import jakarta.validation.constraints.Size;

public record AbsenceResolutionRequest(@Size(max = 500) String resolutionComment) {}
