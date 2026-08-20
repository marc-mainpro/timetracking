package com.tfp.timetracking.absence.interfaces.rest;

import com.tfp.timetracking.absence.application.CancelAbsenceRequestUseCase;
import com.tfp.timetracking.absence.application.ListAbsenceTypesUseCase;
import com.tfp.timetracking.absence.application.ListOwnAbsenceRequestsUseCase;
import com.tfp.timetracking.absence.application.RequestAbsenceUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app")
@Tag(name = "Absences")
public class AppAbsenceController {

    private final ListAbsenceTypesUseCase listAbsenceTypesUseCase;
    private final RequestAbsenceUseCase requestAbsenceUseCase;
    private final ListOwnAbsenceRequestsUseCase listOwnAbsenceRequestsUseCase;
    private final CancelAbsenceRequestUseCase cancelAbsenceRequestUseCase;
    private final AbsenceRestMapper mapper;

    public AppAbsenceController(
            ListAbsenceTypesUseCase listAbsenceTypesUseCase,
            RequestAbsenceUseCase requestAbsenceUseCase,
            ListOwnAbsenceRequestsUseCase listOwnAbsenceRequestsUseCase,
            CancelAbsenceRequestUseCase cancelAbsenceRequestUseCase,
            AbsenceRestMapper mapper) {
        this.listAbsenceTypesUseCase = listAbsenceTypesUseCase;
        this.requestAbsenceUseCase = requestAbsenceUseCase;
        this.listOwnAbsenceRequestsUseCase = listOwnAbsenceRequestsUseCase;
        this.cancelAbsenceRequestUseCase = cancelAbsenceRequestUseCase;
        this.mapper = mapper;
    }

    /**
     * El catalogo tambien lo necesita el {@code TENANT_ADMIN}: la pantalla de
     * resolucion de ausencias traduce el {@code absenceTypeId} de cada
     * solicitud a su nombre. No amplia el alcance de los datos, porque el caso
     * de uso filtra siempre por el tenant del token.
     */
    @GetMapping("/absence-types")
    @PreAuthorize("hasAnyRole('EMPLOYEE','TENANT_ADMIN')")
    public List<AbsenceTypeResponse> types() {
        return mapper.toTypeResponse(listAbsenceTypesUseCase.listActive());
    }

    @PostMapping("/absences")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public AbsenceResponse request(@Valid @RequestBody AbsenceRequestBody request) {
        return mapper.toResponse(requestAbsenceUseCase.request(mapper.toCommand(request)));
    }

    @GetMapping("/absences")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<AbsenceResponse> list(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return mapper.toResponse(listOwnAbsenceRequestsUseCase.list(from, to));
    }

    @PostMapping("/absences/{absenceId}/cancel")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public AbsenceResponse cancel(@PathVariable UUID absenceId) {
        return mapper.toResponse(cancelAbsenceRequestUseCase.cancel(absenceId));
    }
}
