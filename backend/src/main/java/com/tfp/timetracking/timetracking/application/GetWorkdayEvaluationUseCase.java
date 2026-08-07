package com.tfp.timetracking.timetracking.application;

import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.timetracking.domain.WorkdayEvaluation;
import com.tfp.timetracking.timetracking.domain.WorkdayEvaluationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta la evaluacion asociada a una jornada, siempre dentro del tenant
 * autenticado.
 *
 * <p>Existe para que el borde REST no hable directamente con
 * {@link WorkdayEvaluationRepository}: {@code WorkdayRestMapper} tenia el
 * repositorio inyectado, lo que rompia la regla de
 * {@code RestLayerAccessTest} (la capa {@code interfaces} no puede depender de
 * repositorios de dominio). La alternativa de pasar la evaluacion desde el
 * controlador se descarto porque obligaria al controlador a conocer el tipo de
 * dominio {@code WorkdayEvaluation}, que es justo lo que prohibe
 * {@code LayeredArchitectureTest}.
 */
@Service
public class GetWorkdayEvaluationUseCase {

    private final WorkdayEvaluationRepository workdayEvaluationRepository;
    private final TenantContext tenantContext;

    public GetWorkdayEvaluationUseCase(
            WorkdayEvaluationRepository workdayEvaluationRepository, TenantContext tenantContext) {
        this.workdayEvaluationRepository = workdayEvaluationRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public Optional<WorkdayEvaluation> findByWorkdayId(UUID workdayId) {
        return workdayEvaluationRepository.findByWorkdayId(tenantContext.currentTenantId(), workdayId);
    }
}
