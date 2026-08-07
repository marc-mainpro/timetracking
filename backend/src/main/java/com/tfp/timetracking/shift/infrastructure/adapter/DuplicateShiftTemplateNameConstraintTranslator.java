package com.tfp.timetracking.shift.infrastructure.adapter;

import com.tfp.timetracking.shared.application.ConstraintViolationTranslator;
import com.tfp.timetracking.shared.domain.DomainException;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateAlreadyExistsException;
import org.springframework.stereotype.Component;

@Component
public class DuplicateShiftTemplateNameConstraintTranslator implements ConstraintViolationTranslator {

    @Override
    public String constraintName() {
        return "uq_shift_template_tenant_name";
    }

    @Override
    public DomainException translate() {
        return new ShiftTemplateAlreadyExistsException();
    }
}
