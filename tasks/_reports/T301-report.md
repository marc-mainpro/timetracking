## T301 — TenantContext desde el principal autenticado

### Cambios

- Se añadió `TenantContext` en `shared.application` con:
  - `currentTenantId()`
  - `currentUserId()`
  - `currentRoles()`
- Se implementó `JwtTenantContext` leyendo `sub`, `tenantId` y `roles` desde el JWT autenticado en `SecurityContext`.
- Se añadió `AuthenticatedPrincipalStateChecker` como puerto transversal y su implementación `IdentityAuthenticatedPrincipalStateChecker` para validar en cada request autenticada que:
  - el usuario sigue existiendo
  - el usuario sigue activo
  - el tenant sigue activo
  - el `tenantId` del token corresponde al usuario autenticado
- Se añadió `UserStatusFilter` tras autenticación para devolver `401` si el token sigue siendo criptográficamente válido pero el usuario/tenant ya no puede operar.
- Se añadió `CorrelationIdFilter` para generar o reutilizar `X-Correlation-Id`, propagarlo en respuesta y reutilizarlo en Problem Details.
- `GlobalExceptionHandler`, `ProblemDetailsAuthenticationEntryPoint` y `RateLimitFilter` ahora reutilizan el `correlationId` del request en lugar de generar uno nuevo por cada error.

### Pruebas (comandos ejecutados y resultado)

```text
cd backend && mvn -B verify
```

Resultado: **BUILD SUCCESS**. `Tests run: 99, Failures: 0, Errors: 0, Skipped: 0`.

Cobertura nueva específica:

- unitarias de `JwtTenantContext`
- integración de `TenantContext` resolviendo tenant/user/roles desde JWT real
- request autenticada con usuario desactivado tras emitir token -> `401 USER_INACTIVE`
- request autenticada con tenant desactivado tras emitir token -> `401 TENANT_INACTIVE`
- propagación de `X-Correlation-Id` a Problem Details

### Cobertura

- JaCoCo sigue en verde para los umbrales configurados de `domain` y `application`.
- Los nuevos componentes transversales quedan cubiertos con tests unitarios e integración real.

### Seguridad

- El tenant deja de estar implícito en capas superiores y pasa a resolverse exclusivamente desde el principal autenticado vía `TenantContext`.
- Un token no sigue siendo suficiente si el usuario o el tenant han quedado inactivos: cada request autenticada revalida el estado actual.
- El `correlationId` queda unificado por request, mejorando trazabilidad sin exponer información adicional.

### Documentación actualizada

- `docs/api/README.md`
- `docs/architecture/components.md`

### ADR

- No fue necesario añadir ADR nueva: T301 implementa decisiones ya fijadas en SDD/contexto sobre resolución de tenant desde el principal autenticado.

### Riesgos detectados

1. La validación de usuario/tenant activo consulta estado actual en cada request autenticada; es simple y segura para el MVP, pero añade una consulta adicional por request hasta que exista una optimización justificada.
2. `currentRoles()` expone strings en lugar de `identity.domain.Role` para evitar ciclos entre módulos; es una decisión pragmática de frontera y conviene mantenerla consistente en tareas posteriores.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T301.
- La siguiente tarea correcta es `T302` para hacer tenant-aware los repositorios usando `TenantContext` como fuente única de tenant.
