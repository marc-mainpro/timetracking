package com.tfp.timetracking.absence.interfaces.rest;

import java.util.UUID;

public record AbsenceTypeResponse(
        UUID id,
        String code,
        String name,
        boolean requiresApproval,
        boolean allowsAttachment,
        boolean active) {}
