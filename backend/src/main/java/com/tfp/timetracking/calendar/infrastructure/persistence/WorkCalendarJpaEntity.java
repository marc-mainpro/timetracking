package com.tfp.timetracking.calendar.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad JPA de {@code work_calendar} (migracion V16). Separada del agregado de
 * dominio {@code WorkCalendar}: ninguna anotacion de persistencia se filtra al
 * dominio (CONTEXT-GLOBAL §4).
 *
 * <p>{@code valid_from}/{@code valid_to} son {@link LocalDate} y no
 * {@link Instant}: la vigencia es un periodo del calendario civil (RNF-011).
 */
@Entity
@Table(name = "work_calendar")
public class WorkCalendarJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "timezone", nullable = false, length = 60)
    private String timezone;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("dayOfWeek ASC")
    private List<CalendarDayRuleJpaEntity> dayRules = new ArrayList<>();

    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("date ASC")
    private List<CalendarHolidayJpaEntity> holidays = new ArrayList<>();

    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("date ASC")
    private List<CalendarSpecialDayJpaEntity> specialDays = new ArrayList<>();

    protected WorkCalendarJpaEntity() {
        // Requerido por JPA.
    }

    public WorkCalendarJpaEntity(
            UUID id,
            UUID tenantId,
            String name,
            String timezone,
            LocalDate validFrom,
            LocalDate validTo,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.timezone = timezone;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public String getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<CalendarDayRuleJpaEntity> getDayRules() {
        return dayRules;
    }

    public List<CalendarHolidayJpaEntity> getHolidays() {
        return holidays;
    }

    public List<CalendarSpecialDayJpaEntity> getSpecialDays() {
        return specialDays;
    }
}
