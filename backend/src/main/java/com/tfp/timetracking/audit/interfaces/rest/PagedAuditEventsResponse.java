package com.tfp.timetracking.audit.interfaces.rest;

import java.util.List;

public record PagedAuditEventsResponse(
        List<AuditEventResponse> content, int page, int size, long totalElements, int totalPages) {}
