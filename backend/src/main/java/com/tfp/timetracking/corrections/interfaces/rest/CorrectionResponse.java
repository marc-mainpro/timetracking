package com.tfp.timetracking.corrections.interfaces.rest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CorrectionResponse(
        UUID id,
        UUID workdayId,
        UUID requestedBy,
        String reason,
        ProposedChangesResponse proposedChanges,
        String status,
        UUID resolvedBy,
        Instant resolvedAt,
        String resolutionComment,
        Instant createdAt) {

    public record ProposedChangesResponse(Instant startedAt, Instant endedAt, List<ProposedBreakResponse> breaks) {}

    public record ProposedBreakResponse(Instant startedAt, Instant endedAt) {}
}
