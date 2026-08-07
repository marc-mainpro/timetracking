package com.tfp.timetracking.calendar.interfaces.rest;

import com.tfp.timetracking.calendar.application.usecase.ArchiveWorkCalendarUseCase;
import com.tfp.timetracking.calendar.application.usecase.CreateWorkCalendarUseCase;
import com.tfp.timetracking.calendar.application.usecase.GetWorkCalendarUseCase;
import com.tfp.timetracking.calendar.application.usecase.ListWorkCalendarsUseCase;
import com.tfp.timetracking.calendar.application.usecase.UpdateWorkCalendarUseCase;
import com.tfp.timetracking.shared.interfaces.rest.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de administracion de calendarios laborales (T70-05, diseno §11.3).
 * Reservada al {@code TENANT_ADMIN}: un {@code EMPLOYEE} recibe 403.
 *
 * <p>El controlador no tiene logica ni toca repositorios: valida el borde,
 * delega en el caso de uso y traduce con {@link CalendarRestMapper}.
 */
@RestController
@RequestMapping("/api/v1/admin/calendars")
@Tag(name = "Admin - Calendars")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class AdminCalendarController {

    private final CreateWorkCalendarUseCase createWorkCalendarUseCase;
    private final UpdateWorkCalendarUseCase updateWorkCalendarUseCase;
    private final ArchiveWorkCalendarUseCase archiveWorkCalendarUseCase;
    private final GetWorkCalendarUseCase getWorkCalendarUseCase;
    private final ListWorkCalendarsUseCase listWorkCalendarsUseCase;
    private final CalendarRestMapper mapper;

    public AdminCalendarController(
            CreateWorkCalendarUseCase createWorkCalendarUseCase,
            UpdateWorkCalendarUseCase updateWorkCalendarUseCase,
            ArchiveWorkCalendarUseCase archiveWorkCalendarUseCase,
            GetWorkCalendarUseCase getWorkCalendarUseCase,
            ListWorkCalendarsUseCase listWorkCalendarsUseCase,
            CalendarRestMapper mapper) {
        this.createWorkCalendarUseCase = createWorkCalendarUseCase;
        this.updateWorkCalendarUseCase = updateWorkCalendarUseCase;
        this.archiveWorkCalendarUseCase = archiveWorkCalendarUseCase;
        this.getWorkCalendarUseCase = getWorkCalendarUseCase;
        this.listWorkCalendarsUseCase = listWorkCalendarsUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Crea un calendario laboral")
    public ResponseEntity<CalendarDetailResponse> create(@Valid @RequestBody SaveCalendarRequest request) {
        CalendarDetailResponse body =
                mapper.toDetail(createWorkCalendarUseCase.create(mapper.toCommand(request)));
        return ResponseEntity.created(URI.create("/api/v1/admin/calendars/" + body.id())).body(body);
    }

    @GetMapping
    @Operation(summary = "Lista los calendarios del tenant, con filtro opcional por estado")
    public PagedCalendarsResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status) {
        PageQuery pageQuery = PageQuery.of(page, size);
        // El filtro se traduce en el mapper y se encadena sin variable
        // intermedia: asi el controlador no declara ningun tipo de dominio y no
        // hay que anadir una excepcion a LayeredArchitectureTest (ADR-0011: las
        // excepciones de *Controller siguen siendo explicitas a proposito).
        return mapper.toPagedResponse(
                listWorkCalendarsUseCase.list(mapper.toStatus(status), pageQuery.page(), pageQuery.size()));
    }

    @GetMapping("/{calendarId}")
    @Operation(summary = "Consulta el detalle de un calendario")
    public CalendarDetailResponse get(@PathVariable UUID calendarId) {
        return mapper.toDetail(getWorkCalendarUseCase.get(calendarId));
    }

    @PutMapping("/{calendarId}")
    @Operation(summary = "Edita un calendario: sustituye reglas, festivos y jornadas especiales")
    public CalendarDetailResponse update(
            @PathVariable UUID calendarId, @Valid @RequestBody SaveCalendarRequest request) {
        return mapper.toDetail(updateWorkCalendarUseCase.update(calendarId, mapper.toCommand(request)));
    }

    /**
     * Archiva el calendario (borrado logico). No se borra fisicamente porque las
     * jornadas ya calculadas y las asignaciones historicas lo referencian.
     */
    @DeleteMapping("/{calendarId}")
    @Operation(summary = "Archiva un calendario (borrado logico)")
    public ResponseEntity<Void> archive(@PathVariable UUID calendarId) {
        archiveWorkCalendarUseCase.archive(calendarId);
        return ResponseEntity.noContent().build();
    }
}
