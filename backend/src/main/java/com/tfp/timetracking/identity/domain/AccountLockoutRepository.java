package com.tfp.timetracking.identity.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de dominio del agregado {@link AccountLockout}.
 *
 * <p>Tenant-scoped por convencion: {@code tenantId} es el primer parametro de
 * toda consulta de negocio. El caso de uso de autenticacion ya ha resuelto el
 * usuario por email (unico globalmente, ADR-0008) antes de llegar aqui, asi que
 * dispone del tenant del propio agregado {@link User} y nunca de uno enviado
 * por el cliente.
 */
public interface AccountLockoutRepository {

    AccountLockout save(AccountLockout accountLockout);

    Optional<AccountLockout> findByUserId(UUID tenantId, UUID userId);
}
