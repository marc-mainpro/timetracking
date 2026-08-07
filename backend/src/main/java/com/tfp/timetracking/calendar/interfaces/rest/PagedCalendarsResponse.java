package com.tfp.timetracking.calendar.interfaces.rest;

import java.util.List;

/** Pagina de calendarios laborales. */
public record PagedCalendarsResponse(
        List<CalendarSummaryResponse> content, int page, int size, long totalElements, int totalPages) {}
