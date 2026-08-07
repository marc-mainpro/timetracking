package com.tfp.timetracking.shift.domain.model;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

public final class ShiftTemplate {

    private static final int MAX_NAME_LENGTH = 120;

    private final UUID id;
    private final UUID tenantId;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    private ShiftBreakPolicy breakPolicy;
    private ShiftTemplateStatus status;

    private ShiftTemplate(
            UUID id,
            UUID tenantId,
            String name,
            LocalTime startTime,
            LocalTime endTime,
            ShiftBreakPolicy breakPolicy,
            ShiftTemplateStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakPolicy = breakPolicy;
        this.status = status;
    }

    public static ShiftTemplate create(
            UUID tenantId,
            String name,
            LocalTime startTime,
            LocalTime endTime,
            ShiftBreakPolicy breakPolicy,
            UUID id) {
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(id, "id no puede ser null");
        ShiftTemplate template = new ShiftTemplate(
                id,
                tenantId,
                normalizeName(name),
                requireTime(startTime, "startTime"),
                requireTime(endTime, "endTime"),
                normalizePolicy(breakPolicy),
                ShiftTemplateStatus.ACTIVE);
        validateDuration(template.startTime, template.endTime, template.breakPolicy);
        return template;
    }

    public static ShiftTemplate reconstitute(
            UUID id,
            UUID tenantId,
            String name,
            LocalTime startTime,
            LocalTime endTime,
            ShiftBreakPolicy breakPolicy,
            ShiftTemplateStatus status) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(status, "status no puede ser null");
        ShiftTemplate template = new ShiftTemplate(
                id,
                tenantId,
                normalizeName(name),
                requireTime(startTime, "startTime"),
                requireTime(endTime, "endTime"),
                normalizePolicy(breakPolicy),
                status);
        validateDuration(template.startTime, template.endTime, template.breakPolicy);
        return template;
    }

    public void update(String name, LocalTime startTime, LocalTime endTime, ShiftBreakPolicy breakPolicy) {
        requireActive();
        this.name = normalizeName(name);
        this.startTime = requireTime(startTime, "startTime");
        this.endTime = requireTime(endTime, "endTime");
        this.breakPolicy = normalizePolicy(breakPolicy);
        validateDuration(this.startTime, this.endTime, this.breakPolicy);
    }

    public void archive() {
        requireActive();
        this.status = ShiftTemplateStatus.ARCHIVED;
    }

    public boolean crossesMidnight() {
        return endTime.isBefore(startTime);
    }

    public Duration plannedDuration() {
        long minutes = Duration.between(startTime, endTime).toMinutes();
        if (minutes <= 0) {
            minutes += Duration.ofDays(1).toMinutes();
        }
        return Duration.ofMinutes(minutes);
    }

    private void requireActive() {
        if (status == ShiftTemplateStatus.ARCHIVED) {
            throw new IllegalStateException("La plantilla de turno está archivada");
        }
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        String normalized = name.trim();
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("El nombre no puede superar los " + MAX_NAME_LENGTH + " caracteres");
        }
        return normalized;
    }

    private static LocalTime requireTime(LocalTime time, String field) {
        return Objects.requireNonNull(time, field + " no puede ser null");
    }

    private static ShiftBreakPolicy normalizePolicy(ShiftBreakPolicy breakPolicy) {
        return breakPolicy == null ? new ShiftBreakPolicy(Duration.ZERO) : breakPolicy;
    }

    private static void validateDuration(LocalTime startTime, LocalTime endTime, ShiftBreakPolicy breakPolicy) {
        long totalMinutes = Duration.between(startTime, endTime).toMinutes();
        if (totalMinutes <= 0) {
            totalMinutes += Duration.ofDays(1).toMinutes();
        }
        if (totalMinutes <= 0) {
            throw new IllegalArgumentException("El turno debe tener duración positiva");
        }
        if (breakPolicy.plannedBreakDuration().compareTo(Duration.ofMinutes(totalMinutes)) >= 0) {
            throw new IllegalArgumentException("La pausa prevista debe ser menor que la duración del turno");
        }
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String name() { return name; }
    public LocalTime startTime() { return startTime; }
    public LocalTime endTime() { return endTime; }
    public ShiftBreakPolicy breakPolicy() { return breakPolicy; }
    public ShiftTemplateStatus status() { return status; }
}
