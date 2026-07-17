package com.tfp.timetracking.identity.interfaces.rest;

import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EmployeeRestMapper {

    public EmployeeResponse toResponse(User user) {
        return new EmployeeResponse(
                user.id(),
                user.email().value(),
                user.firstName(),
                user.lastName(),
                user.status().name(),
                user.roles().stream().map(Enum::name).collect(Collectors.toSet()),
                user.createdAt(),
                user.updatedAt());
    }

    public PagedEmployeesResponse toPagedResponse(PagedResult<User> result) {
        return new PagedEmployeesResponse(
                result.content().stream().map(this::toResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
