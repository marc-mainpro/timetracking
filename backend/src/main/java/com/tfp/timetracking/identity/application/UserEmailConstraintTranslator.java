package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.EmailAlreadyInUseException;
import com.tfp.timetracking.shared.application.ConstraintViolationTranslator;
import com.tfp.timetracking.shared.domain.DomainException;
import org.springframework.stereotype.Component;

/**
 * Constraint de unicidad global del email de usuario (ADR-0008), definida en
 * la migracion de identidad.
 */
@Component
public class UserEmailConstraintTranslator implements ConstraintViolationTranslator {

    @Override
    public String constraintName() {
        return "uq_app_user_email";
    }

    @Override
    public DomainException translate() {
        return new EmailAlreadyInUseException("");
    }
}
