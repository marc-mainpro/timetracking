package com.tfp.timetracking.identity.interfaces.rest;

import java.util.List;

public record PagedEmployeesResponse(
        List<EmployeeResponse> content, int page, int size, long totalElements, int totalPages) {}
