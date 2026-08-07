package com.tfp.timetracking.tenant.interfaces.rest;

/**
 * Respuesta genérica de los endpoints públicos de alta (RF-REG-005).
 *
 * <p>Deliberadamente <b>sin identificadores ni estado</b>: es el mismo cuerpo
 * para una solicitud nueva, para un correo que ya tenía cuenta y para una
 * solicitud descartada por abuso. Devolver aquí un {@code registrationId} o un
 * estado convertiría el endpoint en un oráculo de qué correos existen.
 *
 * @param message texto neutro para mostrar al usuario
 */
public record TenantRegistrationAcceptedResponse(String message) {

    public static TenantRegistrationAcceptedResponse standard() {
        return new TenantRegistrationAcceptedResponse(
                "Si los datos son correctos, recibirás un correo para confirmar tu dirección.");
    }
}
