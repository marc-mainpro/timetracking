package com.tfp.timetracking.tenant.application;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto para auditar las acciones <b>anónimas</b> del registro público
 * (RF-REG-006).
 *
 * <p>{@code AuditRecorder} no sirve para esto: resuelve tenant y actor desde el
 * JWT, y en el alta pública no hay JWT por definición. Este puerto describe lo
 * que sí hay —una acción sin actor conocido, en el ámbito de la plataforma— y
 * deja que la infraestructura decida cómo se materializa.
 *
 * <p>Los metadatos nunca deben incluir el token de verificación ni la IP en
 * claro (RS-014).
 */
public interface RegistrationAuditTrail {

    void record(String action, UUID registrationId, Map<String, Object> metadata);
}
