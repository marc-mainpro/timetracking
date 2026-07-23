package com.tfp.timetracking.shared.application;

import com.tfp.timetracking.shared.domain.DomainException;

/**
 * Traduce la violacion de una constraint de base de datos conocida al error de
 * negocio ({@link DomainException}) que representa (CONTEXT-GLOBAL §7,
 * ADR-0006).
 *
 * <p>Cada modulo registra como bean sus propias constraints, de modo que
 * {@code shared} no depende de los dominios concretos y el manejador global de
 * errores solo conoce este contrato. La carrera tipica: dos requests
 * concurrentes pasan el chequeo de negocio en el use case y una de ellas acaba
 * chocando contra el indice unico; sin esta traduccion el cliente recibiria un
 * {@code CONCURRENT_MODIFICATION} generico en vez del codigo de negocio
 * estable.
 */
public interface ConstraintViolationTranslator {

    /** Nombre de la constraint o indice unico segun la migracion Flyway. */
    String constraintName();

    /** Error de negocio equivalente a la violacion de la constraint. */
    DomainException translate();
}
