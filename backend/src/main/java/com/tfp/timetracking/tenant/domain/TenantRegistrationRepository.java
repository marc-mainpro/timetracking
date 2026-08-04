package com.tfp.timetracking.tenant.domain;

import com.tfp.timetracking.shared.domain.PagedResult;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de dominio de {@link TenantRegistration}.
 *
 * <p><b>Excepción documentada a la convención «tenantId como primer
 * parámetro»</b> (ver {@code RepositoryTenantConventionTest} y ADR-0002): una
 * solicitud de alta es anterior al tenant —justamente lo que se está pidiendo
 * es que exista uno—, así que no hay ningún {@code tenantId} por el que
 * filtrar. Estos datos son de ámbito plataforma: solo los leen el flujo público
 * (por token, nunca por listado) y el {@code PLATFORM_ADMIN}. El aislamiento
 * aquí no lo da la columna de tenant sino la autorización por rol
 * ({@code hasRole('PLATFORM_ADMIN')}) y el hecho de que ningún endpoint de
 * tenant exponga esta tabla.
 */
public interface TenantRegistrationRepository {

    TenantRegistration save(TenantRegistration registration);

    Optional<TenantRegistration> findById(UUID id);

    /** Busca por el hash del token; el token en claro nunca llega al repositorio. */
    Optional<TenantRegistration> findByVerificationTokenHash(String verificationTokenHash);

    /**
     * Última solicitud viva (no decidida ni caducada) para un correo; se usa
     * para no crear duplicados y para el reenvío (T53-05).
     */
    Optional<TenantRegistration> findOpenByEmail(String email);

    /** Listado paginado para la revisión de plataforma, ordenado por fecha de creación descendente. */
    PagedResult<TenantRegistration> findAll(TenantRegistrationStatus status, int page, int size);

    /** Solicitudes creadas desde una misma IP a partir de un instante (RF-REG-003). */
    long countByIpHashSince(String ipHash, Instant since);

    /** Solicitudes creadas para un mismo correo a partir de un instante (RF-REG-003). */
    long countByEmailSince(String email, Instant since);
}
