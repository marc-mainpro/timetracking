package com.tfp.timetracking.identity.interfaces.rest;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String status,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt) {}
