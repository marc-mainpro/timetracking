package com.tfp.timetracking.absence.domain;

import java.util.Objects;
import java.util.UUID;

public final class AbsenceType {

    private static final int MAX_CODE_LENGTH = 40;
    private static final int MAX_NAME_LENGTH = 120;

    private final UUID id;
    private final UUID tenantId;
    private final String code;
    private String name;
    private boolean requiresApproval;
    private boolean allowsAttachment;
    private boolean active;

    private AbsenceType(
            UUID id,
            UUID tenantId,
            String code,
            String name,
            boolean requiresApproval,
            boolean allowsAttachment,
            boolean active) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.requiresApproval = requiresApproval;
        this.allowsAttachment = allowsAttachment;
        this.active = active;
    }

    public static AbsenceType create(
            UUID tenantId,
            String code,
            String name,
            boolean requiresApproval,
            boolean allowsAttachment,
            UUID id) {
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(id, "id no puede ser null");
        return new AbsenceType(
                id,
                tenantId,
                normalizeCode(code),
                normalizeName(name),
                requiresApproval,
                allowsAttachment,
                true);
    }

    public static AbsenceType reconstitute(
            UUID id,
            UUID tenantId,
            String code,
            String name,
            boolean requiresApproval,
            boolean allowsAttachment,
            boolean active) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        return new AbsenceType(
                id,
                tenantId,
                normalizeCode(code),
                normalizeName(name),
                requiresApproval,
                allowsAttachment,
                active);
    }

    public void rename(String name) {
        this.name = normalizeName(name);
    }

    public void configure(boolean requiresApproval, boolean allowsAttachment) {
        this.requiresApproval = requiresApproval;
        this.allowsAttachment = allowsAttachment;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código es obligatorio");
        }
        String normalized = code.trim().toUpperCase();
        if (normalized.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("El código no puede superar los " + MAX_CODE_LENGTH + " caracteres");
        }
        return normalized;
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

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public boolean requiresApproval() {
        return requiresApproval;
    }

    public boolean allowsAttachment() {
        return allowsAttachment;
    }

    public boolean active() {
        return active;
    }
}
