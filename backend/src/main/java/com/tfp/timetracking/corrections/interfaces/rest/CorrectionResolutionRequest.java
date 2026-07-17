package com.tfp.timetracking.corrections.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

public record CorrectionResolutionRequest(String resolutionComment) {

    public CorrectionResolutionRequest {
        if (resolutionComment != null && resolutionComment.isBlank()) {
            resolutionComment = null;
        }
    }
}
