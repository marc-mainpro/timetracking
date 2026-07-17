package com.tfp.timetracking.timetracking.domain;

import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class BreakEntry {

    private final UUID id;
    private final Instant startedAt;
    private Instant endedAt;

    private BreakEntry(UUID id, Instant startedAt, Instant endedAt) {
        this.id = id;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public static BreakEntry start(Instant startedAt, IdGenerator idGenerator) {
        Objects.requireNonNull(startedAt, "startedAt no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        return new BreakEntry(idGenerator.newId(), startedAt, null);
    }

    public static BreakEntry reconstitute(UUID id, Instant startedAt, Instant endedAt) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(startedAt, "startedAt no puede ser null");
        validateChronology(startedAt, endedAt);
        return new BreakEntry(id, startedAt, endedAt);
    }

    public void end(Instant endedAt) {
        Objects.requireNonNull(endedAt, "endedAt no puede ser null");
        validateChronology(startedAt, endedAt);
        this.endedAt = endedAt;
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    private static void validateChronology(Instant startedAt, Instant endedAt) {
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("La pausa no puede finalizar antes de comenzar");
        }
    }

    public UUID id() {
        return id;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }
}
