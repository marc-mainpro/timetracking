package com.tfp.timetracking.shared.interfaces.rest;

import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.domain.DomainException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Conflicto de concurrencia");
        problem.setTitle("Concurrent modification");
        enrich(problem, "CONCURRENT_MODIFICATION");
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
