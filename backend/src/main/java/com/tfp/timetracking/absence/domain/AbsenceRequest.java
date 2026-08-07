package com.tfp.timetracking.absence.domain;

import com.tfp.timetracking.absence.domain.event.AbsenceApproved;
import com.tfp.timetracking.absence.domain.event.AbsenceCancelled;
import com.tfp.timetracking.absence.domain.event.AbsenceRejected;
import com.tfp.timetracking.absence.domain.event.AbsenceRequested;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AbsenceRequest {

    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_RESOLUTION_COMMENT_LENGTH = 500;

    private final UUID id;
    private final UUID tenantId;
    private final UUID employeeId;
    private final UUID absenceTypeId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String reason;
    private AbsenceRequestStatus status;
    private UUID resolvedBy;
    private Instant resolvedAt;
    private String resolutionComment;
    private final Instant createdAt;
    private final List<Object> domainEvents = new ArrayList<>();

    private AbsenceRequest(
            UUID id,
            UUID tenantId,
            UUID employeeId,
            UUID absenceTypeId,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            AbsenceRequestStatus status,
            UUID resolvedBy,
            Instant resolvedAt,
            String resolutionComment,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.absenceTypeId = absenceTypeId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.status = status;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
        this.resolutionComment = resolutionComment;
        this.createdAt = createdAt;
    }

    public static AbsenceRequest request(
            UUID tenantId,
            UUID employeeId,
            UUID absenceTypeId,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            Instant now,
            IdGenerator idGenerator) {
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(employeeId, "employeeId no puede ser null");
        Objects.requireNonNull(absenceTypeId, "absenceTypeId no puede ser null");
        Objects.requireNonNull(now, "now no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        validateRange(startDate, endDate);
        UUID requestId = idGenerator.newId();
        AbsenceRequest request = new AbsenceRequest(
                requestId,
                tenantId,
                employeeId,
                absenceTypeId,
                startDate,
                endDate,
                normalizeNullable(reason, MAX_REASON_LENGTH),
                AbsenceRequestStatus.PENDING,
                null,
                null,
                null,
                now);
        request.domainEvents.add(new AbsenceRequested(
                idGenerator.newId(), now, tenantId, requestId, employeeId, absenceTypeId, startDate, endDate));
        return request;
    }

    public static AbsenceRequest reconstitute(
            UUID id,
            UUID tenantId,
            UUID employeeId,
            UUID absenceTypeId,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            AbsenceRequestStatus status,
            UUID resolvedBy,
            Instant resolvedAt,
            String resolutionComment,
            Instant createdAt) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(employeeId, "employeeId no puede ser null");
        Objects.requireNonNull(absenceTypeId, "absenceTypeId no puede ser null");
        Objects.requireNonNull(status, "status no puede ser null");
        Objects.requireNonNull(createdAt, "createdAt no puede ser null");
        validateRange(startDate, endDate);
        return new AbsenceRequest(
                id,
                tenantId,
                employeeId,
                absenceTypeId,
                startDate,
                endDate,
                normalizeNullable(reason, MAX_REASON_LENGTH),
                status,
                resolvedBy,
                resolvedAt,
                normalizeNullable(resolutionComment, MAX_RESOLUTION_COMMENT_LENGTH),
                createdAt);
    }

    public void approve(UUID resolvedBy, String resolutionComment, Instant now, IdGenerator idGenerator) {
        ensurePending();
        Objects.requireNonNull(resolvedBy, "resolvedBy no puede ser null");
        Objects.requireNonNull(now, "now no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        this.status = AbsenceRequestStatus.APPROVED;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = now;
        this.resolutionComment = normalizeNullable(resolutionComment, MAX_RESOLUTION_COMMENT_LENGTH);
        domainEvents.add(new AbsenceApproved(idGenerator.newId(), now, tenantId, id, employeeId, resolvedBy));
    }

    public void reject(UUID resolvedBy, String resolutionComment, Instant now, IdGenerator idGenerator) {
        ensurePending();
        Objects.requireNonNull(resolvedBy, "resolvedBy no puede ser null");
        Objects.requireNonNull(now, "now no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        this.status = AbsenceRequestStatus.REJECTED;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = now;
        this.resolutionComment = normalizeNullable(resolutionComment, MAX_RESOLUTION_COMMENT_LENGTH);
        domainEvents.add(new AbsenceRejected(idGenerator.newId(), now, tenantId, id, employeeId, resolvedBy));
    }

    public void cancel(Instant now, IdGenerator idGenerator) {
        ensurePending();
        Objects.requireNonNull(now, "now no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        this.status = AbsenceRequestStatus.CANCELLED;
        this.resolvedAt = now;
        domainEvents.add(new AbsenceCancelled(idGenerator.newId(), now, tenantId, id, employeeId));
    }

    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    public boolean isApproved() {
        return status == AbsenceRequestStatus.APPROVED;
    }

    public boolean overlaps(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from no puede ser null");
        Objects.requireNonNull(to, "to no puede ser null");
        return !endDate.isBefore(from) && !startDate.isAfter(to);
    }

    private void ensurePending() {
        if (status != AbsenceRequestStatus.PENDING) {
            throw new AbsenceRequestAlreadyResolvedException();
        }
    }

    private static void validateRange(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate, "startDate no puede ser null");
        Objects.requireNonNull(endDate, "endDate no puede ser null");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La ausencia no puede terminar antes de empezar");
        }
    }

    private static String normalizeNullable(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("El texto no puede superar los " + maxLength + " caracteres");
        }
        return normalized;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID employeeId() { return employeeId; }
    public UUID absenceTypeId() { return absenceTypeId; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public String reason() { return reason; }
    public AbsenceRequestStatus status() { return status; }
    public UUID resolvedBy() { return resolvedBy; }
    public Instant resolvedAt() { return resolvedAt; }
    public String resolutionComment() { return resolutionComment; }
    public Instant createdAt() { return createdAt; }
}
