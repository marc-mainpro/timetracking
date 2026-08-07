package com.tfp.timetracking.calendar.interfaces.rest;

import java.util.List;

/** Pagina de asignaciones de calendario. */
public record PagedCalendarAssignmentsResponse(
        List<CalendarAssignmentResponse> content, int page, int size, long totalElements, int totalPages) {}
