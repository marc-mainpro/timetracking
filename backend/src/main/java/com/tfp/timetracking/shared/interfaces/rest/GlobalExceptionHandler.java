package com.tfp.timetracking.shared.interfaces.rest;

import com.tfp.timetracking.corrections.domain.CorrectionAlreadyPendingException;
import com.tfp.timetracking.identity.domain.EmailAlreadyInUseException;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.domain.DomainException;
import com.tfp.timetracking.timetracking.domain.WorkdayAlreadyOpenException;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejador global de errores (CONTEXT-GLOBAL §7, ADR-0006): traduce las
 * excepciones a RFC 7807 Problem Details, sin stack traces ni detalles
 * internos.
 *
 * <ul>
 *   <li>{@link DomainException}: conflicto de negocio -&gt; 409, con
 *       {@code errorCode} estable (CONTEXT-DOMINIO §2).</li>
 *   <li>{@link IllegalArgumentException}: violacion de una invariante de
 *       construccion del dominio (p. ej. timezone IANA invalida) -&gt; 400.</li>
 *   <li>{@link MethodArgumentNotValidException}: Bean Validation de un DTO de
 *       request -&gt; 400, con detalle por campo ({@code errors}).</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Indice unico parcial que garantiza una unica jornada activa por empleado (V4__timetracking.sql). */
    private static final String ACTIVE_WORKDAY_UNIQUE_INDEX = "ux_workday_active";
    private static final String GLOBAL_USER_EMAIL_UNIQUE_CONSTRAINT = "uq_app_user_email";
    private static final String PENDING_CORRECTION_UNIQUE_INDEX = "ux_correction_request_pending";

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException ex) {
        HttpStatus status = authenticationErrorCode(ex.errorCode()) ? HttpStatus.UNAUTHORIZED : HttpStatus.CONFLICT;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle("Business rule violation");
        enrich(problem, ex.errorCode());
        return problem;
    }

    private boolean authenticationErrorCode(String errorCode) {
        return java.util.Set.of(
                        "INVALID_CREDENTIALS",
                        "INVALID_REFRESH_TOKEN",
                        "REFRESH_TOKEN_REUSED",
                        "USER_INACTIVE",
                        "TENANT_INACTIVE")
                .contains(errorCode);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid request");
        enrich(problem, "INVALID_ARGUMENT");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "La solicitud contiene campos invalidos");
        problem.setTitle("Validation failed");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        problem.setProperty("errors", errors);
        enrich(problem, "VALIDATION_ERROR");
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        enrich(problem, "RESOURCE_NOT_FOUND");
        return problem;
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ProblemDetail handleOptimisticLock(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Conflicto de concurrencia");
        problem.setTitle("Concurrent modification");
        enrich(problem, "CONCURRENT_MODIFICATION");
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        DomainException businessConflict = knownBusinessConflict(ex);
        if (businessConflict != null) {
            return handleDomainException(businessConflict);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Conflicto de concurrencia");
        problem.setTitle("Concurrent modification");
        enrich(problem, "CONCURRENT_MODIFICATION");
        return problem;
    }

    private DomainException knownBusinessConflict(DataIntegrityViolationException ex) {
        String constraintName = violatedConstraintName(ex);
        if (ACTIVE_WORKDAY_UNIQUE_INDEX.equals(constraintName)) {
            return new WorkdayAlreadyOpenException();
        }
        if (GLOBAL_USER_EMAIL_UNIQUE_CONSTRAINT.equals(constraintName)) {
            return new EmailAlreadyInUseException("");
        }
        if (PENDING_CORRECTION_UNIQUE_INDEX.equals(constraintName)) {
            return new CorrectionAlreadyPendingException();
        }
        return null;
    }

    /**
     * Nombre de la constraint violada segun Hibernate (agnostico del driver),
     * o {@code null} si la excepcion no envuelve una violacion de constraint.
     * Preferible a hacer {@code contains} sobre el mensaje del driver, que es
     * fragil ante cambios de version o de idioma.
     */
    private String violatedConstraintName(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return constraintViolation.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Acceso denegado");
        problem.setTitle("Access denied");
        enrich(problem, "FORBIDDEN");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception ex) {
        logger.error("Unhandled exception", ex);
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Se ha producido un error interno");
        problem.setTitle("Internal server error");
        enrich(problem, "INTERNAL_SERVER_ERROR");
        return problem;
    }

    private Map<String, String> toFieldError(FieldError fieldError) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("field", fieldError.getField());
        error.put("message", fieldError.getDefaultMessage());
        return error;
    }

    private void enrich(ProblemDetail problem, String errorCode) {
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("correlationId", currentCorrelationId());
        problem.setProperty("timestamp", Instant.now().toString());
    }

    private String currentCorrelationId() {
        String correlationId = MDC.get(com.tfp.timetracking.shared.infrastructure.security.CorrelationIdFilter.MDC_KEY);
        return correlationId != null ? correlationId : UUID.randomUUID().toString();
    }
}
