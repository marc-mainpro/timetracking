package com.tfp.timetracking.shift.application;

import com.tfp.timetracking.shift.domain.model.ShiftAssignment;
import com.tfp.timetracking.shift.domain.model.ShiftAssignmentRepository;
import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de consulta que expone el turno previsto de un empleado en una fecha
 * (T90-06, RF-SHF-005).
 *
 * <p>Existe para que otros modulos —hoy {@code timetracking}, al evaluar una
 * jornada cerrada— puedan preguntar "cuanto tiempo tenia previsto trabajar este
 * empleado este dia" <b>sin acceder al repositorio de {@code shift}</b>: las
 * interacciones entre modulos van por servicios de aplicacion expuestos
 * explicitamente (AGENTS.md, ADR-0001).
 *
 * <p>A diferencia de {@link ListOwnEffectiveShiftsUseCase}, no resuelve el
 * empleado desde {@code TenantContext}: recibe {@code tenantId} y
 * {@code employeeId} como parametros porque se invoca al evaluar la jornada de
 * un empleado cualquiera, no la del usuario autenticado.
 */
@Service
public class ResolvePlannedShiftUseCase {

    private final ShiftAssignmentRepository assignmentRepository;
    private final ShiftTemplateRepository templateRepository;

    public ResolvePlannedShiftUseCase(
            ShiftAssignmentRepository assignmentRepository, ShiftTemplateRepository templateRepository) {
        this.assignmentRepository = assignmentRepository;
        this.templateRepository = templateRepository;
    }

    /**
     * Devuelve el <b>tiempo de trabajo</b> previsto por el turno vigente.
     *
     * <p>Es la duracion del turno <b>menos la pausa prevista</b>, no la duracion
     * bruta: la jornada real se mide descontando las pausas, asi que comparar el
     * trabajo neto registrado contra un previsto que incluye la pausa haria
     * aparecer una desviacion sistematica en todo turno con descanso.
     *
     * <p>Un empleado no puede tener dos asignaciones vigentes solapadas para la
     * misma fecha ({@link ShiftAssignment#overlaps}), asi que lo normal es que
     * haya como mucho una. Si por datos heredados hubiera varias, se toma la de
     * vigencia mas reciente para que el resultado sea determinista y no dependa
     * del orden que devuelva la base de datos.
     *
     * @return el tiempo previsto, o vacio si el empleado no tiene turno asignado
     *     ese dia o si la plantilla asignada ya no existe
     */
    @Transactional(readOnly = true)
    public Optional<Duration> resolveExpectedWorkDuration(UUID tenantId, UUID employeeId, LocalDate date) {
        List<ShiftAssignment> assignments =
                assignmentRepository.findEffectiveByEmployee(tenantId, employeeId, date);
        return assignments.stream()
                .max(Comparator.comparing(ShiftAssignment::validFrom))
                .flatMap(assignment -> templateRepository.findById(tenantId, assignment.shiftTemplateId()))
                .map(ResolvePlannedShiftUseCase::expectedWorkDuration);
    }

    private static Duration expectedWorkDuration(ShiftTemplate template) {
        Duration expected = template.plannedDuration().minus(template.breakPolicy().plannedBreakDuration());
        return expected.isNegative() ? Duration.ZERO : expected;
    }
}
