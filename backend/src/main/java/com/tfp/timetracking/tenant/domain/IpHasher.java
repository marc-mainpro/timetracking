package com.tfp.timetracking.tenant.domain;

/**
 * Puerto de dominio para convertir la IP de origen de una solicitud de alta en
 * una huella no reversible (RF-REG-003).
 *
 * <p>Se guarda la huella y no la IP porque lo único que necesita la regla de
 * negocio es «¿cuántas solicitudes ha hecho este mismo origen?», y esa pregunta
 * se responde igual de bien con un identificador opaco. Guardar la IP en claro
 * añadiría un dato personal al modelo sin ninguna ganancia funcional.
 */
public interface IpHasher {

    /** @return la huella de la IP, o {@code null} si no se conoce la IP de origen */
    String hash(String clientIp);
}
