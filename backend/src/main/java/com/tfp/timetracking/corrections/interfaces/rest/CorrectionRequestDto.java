package com.tfp.timetracking.corrections.interfaces.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record CorrectionRequestDto(
        @NotBlank String reason,
        @NotNull @Valid ProposedChangesDto proposedChanges) {

    public record ProposedChangesDto(
            @NotNull Instant startedAt,
            @NotNull Instant endedAt,
            @NotNull @Valid List<ProposedBreakDto> breaks) {}

    public record ProposedBreakDto(@NotNull Instant startedAt, @NotNull Instant endedAt) {}
}
