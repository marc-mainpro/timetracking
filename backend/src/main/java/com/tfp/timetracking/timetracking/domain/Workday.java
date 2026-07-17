package com.tfp.timetracking.timetracking.domain;

import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.timetracking.domain.event.BreakEnded;
import com.tfp.timetracking.timetracking.domain.event.BreakStarted;
import com.tfp.timetracking.timetracking.domain.event.WorkdayClosed;
import com.tfp.timetracking.timetracking.domain.event.WorkdayStarted;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Workday {

    private final UUID id;
    private final UUID tenantId;
    private final UUID employeeId;
    private WorkdayStatus status;
    private Instant startedAt;
    private Instant endedAt;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<BreakEntry> breaks;
    private final List<Object> domainEvents = new ArrayList<>();

    private Workday(
            UUID id,
            UUID tenantId,
            UUID employeeId,
            WorkdayStatus status,
            Instant startedAt,
            Instant endedAt,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<BreakEntry> breaks) {
        this.id = id;
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.breaks = breaks;
    }

    public static Workday start(UUID tenantId, UUID employeeId, Instant now, IdGenerator idGenerator) {
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(employeeId, "employeeId no puede ser null");
        Objects.requireNonNull(now, "now no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");

        UUID workdayId = idGenerator.newId();
        Workday workday = new Workday(
                workdayId,
                tenantId,
                employeeId,
                WorkdayStatus.OPEN,
                now,
                null,
                0L,
                now,
                now,
                new ArrayList<>());
        workday.domainEvents.add(new WorkdayStarted(idGenerator.newId(), now, tenantId, workdayId, employeeId, now));
        return workday;
    }

    public static Workday reconstitute(
            UUID id,
            UUID tenantId,
            UUID employeeId,
            WorkdayStatus status,
            Instant startedAt,
            Instant endedAt,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<BreakEntry> breaks) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(employeeId, "employeeId no puede ser null");
        Objects.requireNonNull(status, "status no puede ser null");
        Objects.requireNonNull(startedAt, "startedAt no puede ser null");
        Objects.requireNonNull(createdAt, "createdAt no puede ser null");
        Objects.requireNonNull(updatedAt, "updatedAt no puede ser null");
        List<BreakEntry> copiedBreaks = new ArrayList<>(Objects.requireNonNull(breaks, "breaks no puede ser null"));
        validateEndedAt(startedAt, endedAt, "La jornada no puede finalizar antes de comenzar");
        return new Workday(id, tenantId, employeeId, status, startedAt, endedAt, version, createdAt, updatedAt, copiedBreaks);
    }

    public void startBreak(Instant now, IdGenerator idGenerator) {
        Objects.requireNonNull(now, "now no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        if (hasOpenBreak()) {
            throw new BreakAlreadyOpenException();
        }
        ensureStatus(WorkdayStatus.OPEN, new WorkdayNotOpenException());
        BreakEntry breakEntry = BreakEntry.start(now, idGenerator);
        breaks.add(breakEntry);
        status = WorkdayStatus.ON_BREAK;
        updatedAt = now;
        domainEvents.add(new BreakStarted(idGenerator.newId(), now, tenantId, id, breakEntry.id(), now));
    }

    public void endBreak(Instant now, IdGenerator idGenerator) {
        Objects.requireNonNull(now, "now no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        if (status != WorkdayStatus.ON_BREAK) {
            throw new BreakNotOpenException();
        }
        BreakEntry breakEntry = openBreak().orElseThrow(BreakNotOpenException::new);
        breakEntry.end(now);
        status = WorkdayStatus.OPEN;
        updatedAt = now;
        domainEvents.add(new BreakEnded(idGenerator.newId(), now, tenantId, id, breakEntry.id(), now));
    }

    public void close(Instant now, IdGenerator idGenerator) {
        Objects.requireNonNull(now, "now no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        if (status == WorkdayStatus.ON_BREAK) {
            throw new WorkdayOpenBreakException();
        }
        if (status == WorkdayStatus.CLOSED || status == WorkdayStatus.ADJUSTED) {
            throw new WorkdayAlreadyClosedException();
        }
        if (status != WorkdayStatus.OPEN) {
            throw new WorkdayNotOpenException();
        }
        validateEndedAt(startedAt, now, "La jornada no puede finalizar antes de comenzar");
        endedAt = now;
        status = WorkdayStatus.CLOSED;
        updatedAt = now;
        domainEvents.add(new WorkdayClosed(idGenerator.newId(), now, tenantId, id, employeeId, startedAt, now));
    }

    public void adjust(WorkdayAdjustment changes, Instant now, IdGenerator idGenerator) {
        Objects.requireNonNull(changes, "changes no puede ser null");
        Objects.requireNonNull(now, "now no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        if (status == WorkdayStatus.ADJUSTED || status == WorkdayStatus.CLOSED) {
            if (status == WorkdayStatus.ADJUSTED) {
                throw new WorkdayAlreadyClosedException();
            }
        } else if (status == WorkdayStatus.ON_BREAK) {
            throw new WorkdayOpenBreakException();
        } else {
            throw new WorkdayNotOpenException();
        }

        validateEndedAt(changes.startedAt(), changes.endedAt(), "La jornada ajustada no puede finalizar antes de comenzar");
        List<BreakEntry> adjustedBreaks = new ArrayList<>();
        for (WorkdayAdjustment.AdjustedBreak adjustedBreak : changes.breaks()) {
            adjustedBreaks.add(BreakEntry.reconstitute(idGenerator.newId(), adjustedBreak.startedAt(), adjustedBreak.endedAt()));
        }
        startedAt = changes.startedAt();
        endedAt = changes.endedAt();
        breaks.clear();
        breaks.addAll(adjustedBreaks);
        status = WorkdayStatus.ADJUSTED;
        updatedAt = now;
    }

    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void ensureStatus(WorkdayStatus expected, RuntimeException exception) {
        if (status != expected) {
            throw exception;
        }
    }

    private boolean hasOpenBreak() {
        return openBreak().isPresent();
    }

    private java.util.Optional<BreakEntry> openBreak() {
        return breaks.stream().filter(BreakEntry::isOpen).findFirst();
    }

    private static void validateEndedAt(Instant startedAt, Instant endedAt, String message) {
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(message);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID employeeId() {
        return employeeId;
    }

    public WorkdayStatus status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<BreakEntry> breaks() {
        return List.copyOf(breaks);
    }
}
