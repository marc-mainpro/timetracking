package com.tfp.timetracking.identity.interfaces.rest;

import com.tfp.timetracking.identity.application.ActivateEmployeeUseCase;
import com.tfp.timetracking.identity.application.AssignRoleUseCase;
import com.tfp.timetracking.identity.application.CreateEmployeeCommand;
import com.tfp.timetracking.identity.application.CreateEmployeeUseCase;
import com.tfp.timetracking.identity.application.DeactivateEmployeeUseCase;
import com.tfp.timetracking.identity.application.EmployeeRolesCommand;
import com.tfp.timetracking.identity.application.GetEmployeeUseCase;
import com.tfp.timetracking.identity.application.ListEmployeesUseCase;
import com.tfp.timetracking.identity.application.UpdateEmployeeCommand;
import com.tfp.timetracking.identity.application.UpdateEmployeeUseCase;
import com.tfp.timetracking.identity.domain.UserStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
@Tag(name = "Employees")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class EmployeeController {

    private final CreateEmployeeUseCase createEmployeeUseCase;
    private final UpdateEmployeeUseCase updateEmployeeUseCase;
    private final ActivateEmployeeUseCase activateEmployeeUseCase;
    private final DeactivateEmployeeUseCase deactivateEmployeeUseCase;
    private final AssignRoleUseCase assignRoleUseCase;
    private final ListEmployeesUseCase listEmployeesUseCase;
    private final GetEmployeeUseCase getEmployeeUseCase;
    private final EmployeeRestMapper employeeRestMapper;

    public EmployeeController(
            CreateEmployeeUseCase createEmployeeUseCase,
            UpdateEmployeeUseCase updateEmployeeUseCase,
            ActivateEmployeeUseCase activateEmployeeUseCase,
            DeactivateEmployeeUseCase deactivateEmployeeUseCase,
            AssignRoleUseCase assignRoleUseCase,
            ListEmployeesUseCase listEmployeesUseCase,
            GetEmployeeUseCase getEmployeeUseCase,
            EmployeeRestMapper employeeRestMapper) {
        this.createEmployeeUseCase = createEmployeeUseCase;
        this.updateEmployeeUseCase = updateEmployeeUseCase;
        this.activateEmployeeUseCase = activateEmployeeUseCase;
        this.deactivateEmployeeUseCase = deactivateEmployeeUseCase;
        this.assignRoleUseCase = assignRoleUseCase;
        this.listEmployeesUseCase = listEmployeesUseCase;
        this.getEmployeeUseCase = getEmployeeUseCase;
        this.employeeRestMapper = employeeRestMapper;
    }

    @GetMapping
    public PagedEmployeesResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        UserStatus userStatus = status != null ? UserStatus.valueOf(status) : null;
        return employeeRestMapper.toPagedResponse(listEmployeesUseCase.list(page, size, userStatus));
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse response = employeeRestMapper.toResponse(createEmployeeUseCase.create(new CreateEmployeeCommand(
                request.email(), request.password(), request.firstName(), request.lastName(), request.roles())));
        return ResponseEntity.created(URI.create("/api/v1/employees/" + response.id())).body(response);
    }

    @GetMapping("/{employeeId}")
    public EmployeeResponse get(@PathVariable UUID employeeId) {
        return employeeRestMapper.toResponse(getEmployeeUseCase.get(employeeId));
    }

    @PutMapping("/{employeeId}")
    public EmployeeResponse update(@PathVariable UUID employeeId, @Valid @RequestBody UpdateEmployeeRequest request) {
        return employeeRestMapper.toResponse(updateEmployeeUseCase.update(
                new UpdateEmployeeCommand(employeeId, request.firstName(), request.lastName())));
    }

    @PatchMapping("/{employeeId}/activate")
    public EmployeeResponse activate(@PathVariable UUID employeeId) {
        return employeeRestMapper.toResponse(activateEmployeeUseCase.activate(employeeId));
    }

    @PatchMapping("/{employeeId}/deactivate")
    public EmployeeResponse deactivate(@PathVariable UUID employeeId) {
        return employeeRestMapper.toResponse(deactivateEmployeeUseCase.deactivate(employeeId));
    }

    @PutMapping("/{employeeId}/roles")
    public EmployeeResponse assignRoles(@PathVariable UUID employeeId, @Valid @RequestBody AssignRolesRequest request) {
        return employeeRestMapper.toResponse(assignRoleUseCase.assign(new EmployeeRolesCommand(employeeId, request.roles())));
    }
}
