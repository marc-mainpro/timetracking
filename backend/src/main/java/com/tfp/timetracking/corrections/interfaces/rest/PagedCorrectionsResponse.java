package com.tfp.timetracking.corrections.interfaces.rest;

import java.util.List;

public record PagedCorrectionsResponse(
        List<CorrectionResponse> content, int page, int size, long totalElements, int totalPages) {}
