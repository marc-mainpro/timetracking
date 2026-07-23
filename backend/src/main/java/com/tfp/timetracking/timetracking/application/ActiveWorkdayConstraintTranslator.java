package com.tfp.timetracking.timetracking.application;

import com.tfp.timetracking.shared.application.ConstraintViolationTranslator;
import com.tfp.timetracking.shared.domain.DomainException;
import com.tfp.timetracking.timetracking.domain.WorkdayAlreadyOpenException;
import org.springframework.stereotype.Component;

/**
 * Indice unico parcial que garantiza una unica jornada activa por empleado
 * (V4__timetracking.sql).
 */
@Component
public class ActiveWorkdayConstraintTranslator implements ConstraintViolationTranslator {

    @Override
    public String constraintName() {
        return "ux_workday_active";
    }

    @Override
    public DomainException translate() {
        return new WorkdayAlreadyOpenException();
    }
}
