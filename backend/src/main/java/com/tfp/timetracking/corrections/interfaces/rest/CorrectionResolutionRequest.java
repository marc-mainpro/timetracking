package com.tfp.timetracking.corrections.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CorrectionResolutionRequest(@Size(max = 500) String resolutionComment) {

    public CorrectionResolutionRequest {
        if (resolutionComment != null && resolutionComment.isBlank()) {
            resolutionComment = null;
        }
    }
}
