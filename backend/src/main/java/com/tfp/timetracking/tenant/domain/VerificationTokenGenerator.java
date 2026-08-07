package com.tfp.timetracking.tenant.domain;

/**
 * Puerto de dominio para generar tokens de verificación de correo (T53-05).
 *
 * <p>Se pasa como <b>parámetro de método</b> a las operaciones del agregado
 * {@link TenantRegistration}, igual que {@code Clock} e {@code IdGenerator}: el
 * agregado no guarda colaboradores como campos.
 *
 * <p>La implementación vive en infraestructura y debe usar un generador
 * criptográficamente seguro con al menos 256 bits de entropía.
 */
public interface VerificationTokenGenerator {

    VerificationToken generate();

    /**
     * Calcula el hash de un token en claro recibido del usuario, con el mismo
     * algoritmo que {@link #generate()}, para poder compararlo con el valor
     * almacenado sin conocer nunca el token original.
     */
    String hash(String rawToken);
}
