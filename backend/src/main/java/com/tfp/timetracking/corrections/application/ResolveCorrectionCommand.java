package com.tfp.timetracking.corrections.application;

import java.util.UUID;

public record ResolveCorrectionCommand(UUID correctionId, String resolutionComment) {}
