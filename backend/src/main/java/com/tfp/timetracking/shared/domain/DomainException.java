package com.tfp.timetracking.shared.domain;

/**
 * Excepcion base para todas las violaciones de reglas de negocio del dominio.
 *
 * <p>No depende de Spring, JPA ni de ninguna capa externa: es dominio puro.
 * Cada modulo debe extenderla con excepciones especificas y un
 * {@code errorCode} estable (ver CONTEXT-GLOBAL §7) que la capa
 * {@code interfaces.rest} traduce a un Problem Details (RFC 7807).
 */
public abstract class DomainException extends RuntimeException {

    private final String errorCode;

    protected DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected DomainException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
