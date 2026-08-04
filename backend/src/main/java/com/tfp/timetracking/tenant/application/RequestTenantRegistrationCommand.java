package com.tfp.timetracking.tenant.application;

/**
 * Datos de una solicitud de alta pública (RF-REG-002).
 *
 * @param companyName nombre de la organización
 * @param ownerFirstName nombre del propietario
 * @param ownerLastName apellidos del propietario
 * @param email correo del propietario
 * @param password contraseña en claro; se hashea antes de tocar el dominio de
 *     tenant y nunca se persiste tal cual
 * @param timezone zona horaria IANA de la organización
 * @param source canal de entrada de la solicitud
 * @param clientIp IP de origen; se convierte en huella antes de persistirse
 */
public record RequestTenantRegistrationCommand(
        String companyName,
        String ownerFirstName,
        String ownerLastName,
        String email,
        String password,
        String timezone,
        String source,
        String clientIp) {}
