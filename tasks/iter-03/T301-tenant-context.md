# T301 — TenantContext desde el principal autenticado

- Iteración: 3 · Depende de: T204 · Contexto: CONTEXT-GLOBAL §5

## Objetivo
Resolver el tenant SIEMPRE desde el token autenticado y ponerlo a disposición de la capa de aplicación.

## Detalle
1. Puerto `TenantContext` en `shared.application` (o similar): `UUID currentTenantId()`, `UUID currentUserId()`, `Set<Role> currentRoles()`.
2. Implementación en `shared.infrastructure.security` leyendo los claims del JWT del `SecurityContext`. Si no hay autenticación → excepción (nunca valor por defecto).
3. Prohibir de facto tenant del cliente: revisar que NINGÚN DTO/endpoint existente acepte `tenantId` como entrada; eliminar cualquiera que exista.
4. Validación en cada request autenticada de que el tenant sigue ACTIVO y el usuario sigue ACTIVO (filtro tras autenticación; usuario desactivado con token aún válido → 401). Cachear con TTL corto si es trivial; si no, consulta directa (KISS).
5. Propagar `correlationId` por request (filtro que lo genera/lee de cabecera `X-Correlation-Id` y lo añade al MDC y a los Problem Details).

## Pruebas
- Unitarias del adaptador de claims. Integración: petición con token de tenant A obtiene `currentTenantId()` A; usuario desactivado tras emitir token → 401; sin autenticación → error.

## Fuera de alcance
Repositorios tenant-aware (T302).

## Criterios de aceptación
- `mvn verify` verde; ningún endpoint acepta tenantId del cliente.

## Ficheros previstos
`shared/application/TenantContext.java`, `shared/infrastructure/security/JwtTenantContext.java`, `shared/infrastructure/security/UserStatusFilter.java`, `CorrelationIdFilter.java`, tests.
