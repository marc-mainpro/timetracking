package com.tfp.timetracking.corrections.interfaces.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record CorrectionRequestDto(
        @NotBlank @Size(max = 500) String reason,
        @NotNull @Valid ProposedChangesDto proposedChanges) {

    public record ProposedChangesDto(
            @NotNull Instant startedAt,
            @NotNull Instant endedAt,
            @NotNull @Valid @Size(max = 24) List<ProposedBreakDto> breaks) {}

    public record ProposedBreakDto(@NotNull Instant startedAt, @NotNull Instant endedAt) {}
}
