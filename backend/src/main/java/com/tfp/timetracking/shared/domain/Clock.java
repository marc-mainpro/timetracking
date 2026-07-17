package com.tfp.timetracking.shared.domain;

import java.time.Instant;

/**
 * Puerto de dominio para obtener el instante actual.
 *
 * <p>El dominio nunca invoca {@code Instant.now()} directamente: depende de
 * este puerto para poder sustituirlo por un reloj fijo/controlado en tests.
 * La implementacion real vive en la capa de infraestructura.
 */
public interface Clock {

    Instant now();
}
