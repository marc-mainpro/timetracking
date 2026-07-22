package com.tfp.timetracking.shared.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsGlobalUniqueEmailConstraintToBusinessError() {
        ProblemDetail problem = handler.handleDataIntegrityViolation(dataIntegrityViolation("uq_app_user_email"));

        assertBusinessConflict(problem, "EMAIL_ALREADY_IN_USE", "No se ha podido completar la operacion con los datos proporcionados");
    }

    @Test
    void mapsPendingCorrectionConstraintToBusinessError() {
        ProblemDetail problem = handler.handleDataIntegrityViolation(dataIntegrityViolation("ux_correction_request_pending"));

        assertBusinessConflict(problem, "CORRECTION_ALREADY_PENDING", "Ya existe una solicitud pendiente para esta jornada y usuario");
    }

    @Test
    void keepsActiveWorkdayConstraintMappedToBusinessError() {
        ProblemDetail problem = handler.handleDataIntegrityViolation(dataIntegrityViolation("ux_workday_active"));

        assertBusinessConflict(problem, "WORKDAY_ALREADY_OPEN", "El empleado ya tiene una jornada activa");
    }

    @Test
    void keepsUnknownConstraintAsConcurrentModification() {
        ProblemDetail problem = handler.handleDataIntegrityViolation(dataIntegrityViolation("unknown_constraint"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Concurrent modification");
        assertThat(problem.getDetail()).isEqualTo("Conflicto de concurrencia");
        assertThat(problem.getProperties()).containsEntry("errorCode", "CONCURRENT_MODIFICATION");
    }

    private void assertBusinessConflict(ProblemDetail problem, String errorCode, String detail) {
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Business rule violation");
        assertThat(problem.getDetail()).isEqualTo(detail);
        assertThat(problem.getProperties()).containsEntry("errorCode", errorCode);
    }

    private DataIntegrityViolationException dataIntegrityViolation(String constraintName) {
        ConstraintViolationException constraintViolation =
                new ConstraintViolationException("duplicate", new SQLException("duplicate"), constraintName);
        return new DataIntegrityViolationException("integrity", new RuntimeException(constraintViolation));
    }
}
