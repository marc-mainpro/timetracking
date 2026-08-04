package com.tfp.timetracking.calendar.interfaces.rest;

import com.tfp.timetracking.calendar.application.usecase.AssignCalendarUseCase;
import com.tfp.timetracking.calendar.application.usecase.ListCalendarAssignmentsUseCase;
import com.tfp.timetracking.calendar.application.usecase.RemoveCalendarAssignmentUseCase;
import com.tfp.timetracking.calendar.application.usecase.ResolveEffectiveCalendarUseCase;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.interfaces.rest.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de asignaciones de calendario y de consulta del calendario efectivo
 * (T70-05, RF-CAL-006). Reservada al {@code TENANT_ADMIN}.
 *
 * <p>Vive en su propia ruta base ({@code /api/v1/admin/calendar-assignments}) y
 * no colgando de {@code /api/v1/admin/calendars/...} para no competir con el
 * patron {@code /{calendarId}}: un segmento literal como {@code /assignments}
 * junto a una variable de ruta funciona, pero deja la API a merced del orden de
 * resolucion de patrones de Spring MVC.
 */
@RestController
@RequestMapping("/api/v1/admin/calendar-assignments")
@Tag(name = "Admin - Calendar assignments")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class AdminCalendarAssignmentController {

    private final AssignCalendarUseCase assignCalendarUseCase;
    private final RemoveCalendarAssignmentUseCase removeCalendarAssignmentUseCase;
    private final ListCalendarAssignmentsUseCase listCalendarAssignmentsUseCase;
    private final ResolveEffectiveCalendarUseCase resolveEffectiveCalendarUseCase;
    private final CalendarRestMapper mapper;

    public AdminCalendarAssignmentController(
            AssignCalendarUseCase assignCalendarUseCase,
            RemoveCalendarAssignmentUseCase removeCalendarAssignmentUseCase,
            ListCalendarAssignmentsUseCase listCalendarAssignmentsUseCase,
            ResolveEffectiveCalendarUseCase resolveEffectiveCalendarUseCase,
            CalendarRestMapper mapper) {
        this.assignCalendarUseCase = assignCalendarUseCase;
        this.removeCalendarAssignmentUseCase = removeCalendarAssignmentUseCase;
        this.listCalendarAssignmentsUseCase = listCalendarAssignmentsUseCase;
        this.resolveEffectiveCalendarUseCase = resolveEffectiveCalendarUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Asigna un calendario a todo el tenant, a un equipo o a un empleado")
    public ResponseEntity<CalendarAssignmentResponse> assign(@Valid @RequestBody AssignCalendarRequest request) {
        CalendarAssignmentResponse body = mapper.toResponse(assignCalendarUseCase.assign(mapper.toCommand(request)));
        return ResponseEntity.created(URI.create("/api/v1/admin/calendar-assignments/" + body.id()))
                .body(body);
    }

    @GetMapping
    @Operation(summary = "Lista las asignaciones del tenant, con filtro opcional por calendario")
    public PagedCalendarAssignmentsResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) UUID calendarId) {
        PageQuery pageQuery = PageQuery.of(page, size);
        return mapper.toPagedAssignmentsResponse(
                listCalendarAssignmentsUseCase.list(calendarId, pageQuery.page(), pageQuery.size()));
    }

    @DeleteMapping("/{assignmentId}")
    @Operation(summary = "Retira una asignacion de calendario")
    public ResponseEntity<Void> remove(@PathVariable UUID assignmentId) {
        removeCalendarAssignmentUseCase.remove(assignmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Resuelve el calendario efectivo de un empleado en una fecha aplicando la
     * precedencia empleado &gt; equipo &gt; tenant.
     *
     * <p>{@code teamId} lo aporta el llamante: el sistema no gestiona equipos
     * (ADR-0013) y este modulo no sabe a cual pertenece un empleado. Si no se
     * envia, las asignaciones de equipo quedan descartadas.
     *
     * <p>Cuando ningun calendario disponible cubre esa fecha se responde 404: es
     * la ausencia de recurso "calendario efectivo", no un error de negocio.
     */
    @GetMapping("/effective")
    @Operation(summary = "Resuelve el calendario efectivo de un empleado en una fecha")
    public EffectiveCalendarResponse effective(
            @RequestParam UUID employeeId,
            @RequestParam(required = false) UUID teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return resolveEffectiveCalendarUseCase
                .resolveView(employeeId, teamId, date)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay ningun calendario aplicable para ese empleado en esa fecha"));
    }
}
