package com.tfp.timetracking.timetracking.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "break_entry")
public class BreakEntryJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workday_id", nullable = false)
    private WorkdayJpaEntity workday;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected BreakEntryJpaEntity() {}

    public BreakEntryJpaEntity(UUID id, WorkdayJpaEntity workday, Instant startedAt, Instant endedAt) {
        this.id = id;
        this.workday = workday;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public UUID getId() {
        return id;
    }

    public WorkdayJpaEntity getWorkday() {
        return workday;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
