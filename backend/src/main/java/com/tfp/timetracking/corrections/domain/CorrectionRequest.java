package com.tfp.timetracking.corrections.domain;

import com.tfp.timetracking.corrections.domain.event.CorrectionApproved;
import com.tfp.timetracking.corrections.domain.event.CorrectionRejected;
import com.tfp.timetracking.corrections.domain.event.CorrectionRequested;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CorrectionRequest {

    private final UUID id;
    private final UUID tenantId;
    private final UUID workdayId;
    private final UUID requestedBy;
    private final String reason;
    private final ProposedChanges proposedChanges;
    private CorrectionRequestStatus status;
    private UUID resolvedBy;
    private Instant resolvedAt;
    private String resolutionComment;
    private final Instant createdAt;
    private final List<Object> domainEvents = new ArrayList<>();

    private CorrectionRequest(
            UUID id,
            UUID tenantId,
            UUID workdayId,
            UUID requestedBy,
            String reason,
            ProposedChanges proposedChanges,
            CorrectionRequestStatus status,
            UUID resolvedBy,
            Instant resolvedAt,
            String resolutionComment,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.workdayId = workdayId;
        this.requestedBy = requestedBy;
        this.reason = reason;
        this.proposedChanges = proposedChanges;
        this.status = status;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
        this.resolutionComment = resolutionComment;
        this.createdAt = createdAt;
    }

    public static CorrectionRequest request(
            UUID tenantId,
            UUID workdayId,
            UUID requestedBy,
            String reason,
            ProposedChanges proposedChanges,
            Instant now,
            IdGenerator idGenerator) {
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(workdayId, "workdayId no puede ser null");
        Objects.requireNonNull(requestedBy, "requestedBy no puede ser null");
        Objects.requireNonNull(proposedChanges, "proposedChanges no puede ser null");
        Objects.requireNonNull(now, "now no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        String validatedReason = requireNonBlank(reason, "La razon de la correccion es obligatoria");
        UUID id = idGenerator.newId();
        CorrectionRequest correctionRequest = new CorrectionRequest(
                id,
                tenantId,
                workdayId,
                requestedBy,
                validatedReason,
                proposedChanges,
                CorrectionRequestStatus.PENDING,
                null,
                null,
                null,
                now);
        correctionRequest.domainEvents.add(new CorrectionRequested(idGenerator.newId(), now, tenantId, id, workdayId, requestedBy));
        return correctionRequest;
    }

    public static CorrectionRequest reconstitute(
            UUID id,
            UUID tenantId,
            UUID workdayId,
            UUID requestedBy,
            String reason,
            ProposedChanges proposedChanges,
            CorrectionRequestStatus status,
            UUID resolvedBy,
            Instant resolvedAt,
            String resolutionComment,
            Instant createdAt) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(workdayId, "workdayId no puede ser null");
        Objects.requireNonNull(requestedBy, "requestedBy no puede ser null");
        Objects.requireNonNull(proposedChanges, "proposedChanges no puede ser null");
        Objects.requireNonNull(status, "status no puede ser null");
        Objects.requireNonNull(createdAt, "createdAt no puede ser null");
        return new CorrectionRequest(
                id,
                tenantId,
                workdayId,
                requestedBy,
                requireNonBlank(reason, "La razon de la correccion es obligatoria"),
                proposedChanges,
                status,
                resolvedBy,
                resolvedAt,
                resolutionComment,
                createdAt);
    }

    public void approve(UUID resolvedBy, String comment, Instant now, IdGenerator idGenerator) {
        ensurePending();
        Objects.requireNonNull(resolvedBy, "resolvedBy no puede ser null");
        Objects.requireNonNull(now, "now no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        this.status = CorrectionRequestStatus.APPROVED;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = now;
        this.resolutionComment = normalizeNullable(comment);
        domainEvents.add(new CorrectionApproved(idGenerator.newId(), now, tenantId, id, workdayId, resolvedBy));
    }

    public void reject(UUID resolvedBy, String comment, Instant now, IdGenerator idGenerator) {
        ensurePending();
        Objects.requireNonNull(resolvedBy, "resolvedBy no puede ser null");
        Objects.requireNonNull(now, "now no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        this.status = CorrectionRequestStatus.REJECTED;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = now;
        this.resolutionComment = requireNonBlank(comment, "El comentario de rechazo es obligatorio");
        domainEvents.add(new CorrectionRejected(idGenerator.newId(), now, tenantId, id, workdayId, resolvedBy));
    }

    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void ensurePending() {
        if (status != CorrectionRequestStatus.PENDING) {
            throw new CorrectionAlreadyResolvedException();
        }
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID workdayId() { return workdayId; }
    public UUID requestedBy() { return requestedBy; }
    public String reason() { return reason; }
    public ProposedChanges proposedChanges() { return proposedChanges; }
    public CorrectionRequestStatus status() { return status; }
    public UUID resolvedBy() { return resolvedBy; }
    public Instant resolvedAt() { return resolvedAt; }
    public String resolutionComment() { return resolutionComment; }
    public Instant createdAt() { return createdAt; }
}
