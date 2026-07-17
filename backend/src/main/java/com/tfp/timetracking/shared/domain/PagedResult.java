package com.tfp.timetracking.shared.domain;

import java.util.List;

public record PagedResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PagedResult {
        content = List.copyOf(content);
    }
}
