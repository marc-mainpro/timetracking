## T204 — Autenticación: login, refresh rotatorio, logout

### Cambios

- Se implementaron los casos de uso `AuthenticateUser`, `RefreshSession` y `LogoutUser` en `identity.application`.
- Se añadió autenticación JWT HS256 con `spring-boot-starter-oauth2-resource-server`, claims `sub`, `tenantId` y `roles`, access token corto y refresh token opaco rotatorio.
- Se incorporó persistencia de `refresh_token` con adaptador JPA, hash SHA-256, rotación, revocación y detección de reutilización.
- Se expusieron `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh` y `POST /api/v1/auth/logout`.
- `SecurityConfig` pasó a proteger por defecto el resto de endpoints con Bearer JWT, CORS restringido y respuestas Problem Details para 401/403.
- Se cambió la unicidad de `app_user.email` a nivel global mediante `V3__global_unique_user_email.sql` y `RegisterTenant` ahora valida duplicados globales.
- Se añadió `TenantAccessRepository` en `identity` para comprobar el estado del tenant sin introducir ciclos Java con el módulo `tenant`.

### Pruebas (comandos ejecutados y resultado)

```text
cd backend && mvn -B verify
```

Resultado: **BUILD SUCCESS**. `Tests run: 84, Failures: 0, Errors: 0, Skipped: 0`.

Cobertura funcional nueva validada por tests unitarios e integración:

- login feliz, password inválido, usuario inactivo, tenant inactivo
- refresh rotatorio
- reutilización de refresh token con invalidación de cadena
- refresh expirado
- logout con revocación
- anónimo a recurso privado → 401
- bearer inválido/expirado → 401
- ausencia de fuga de password/token en logs de la autenticación

### Cobertura

- JaCoCo sigue en verde para los gates configurados de `domain` y `application`.
- El nuevo código de autenticación queda cubierto por tests unitarios e integración reales.

### Seguridad

- JWT firmado HS256 con secreto por entorno (`JWT_SECRET`), TTL de 15 minutos.
- Refresh token opaco hasheado con SHA-256 y enviado solo en cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`.
- Reutilización de refresh token ya rotado revoca la cadena activa y responde `401`.
- Se añadieron `Problem Details` consistentes para 401/403 sin exponer detalles internos.
- El login sigue resolviendo tenant desde el usuario autenticado; no se confía en `tenant_id` del cliente.

### Documentación actualizada

- `docs/api/README.md`
- `docs/domain/reglas-de-negocio.md`
- `docs/domain/agregados.md`
- `docs/security/threat-model.md`
- `docs/testing/informe-cobertura.md`
- `tasks/_context/CONTEXT-GLOBAL.md`
- `tasks/_context/CONTEXT-DOMINIO.md`
- `SDD-MVP-control-horario.md`

### ADR

- **ADR-0008**: `app_user.email` pasa a ser globalmente único para soportar autenticación por `email + password` sin ambigüedad entre tenants.

### Riesgos detectados

1. `logout` quedó protegido con Bearer JWT para respetar la restricción de `SecurityConfig` de T204; si en una iteración futura se quiere permitir cierre de sesión con solo refresh cookie, habrá que registrar el ajuste de contrato/seguridad.
2. El modelo de unicidad global de email simplifica autenticación, pero restringe un caso de negocio potencial en SaaS B2B donde una misma persona podría pertenecer a más de un tenant con el mismo correo. La decisión queda documentada en ADR-0008.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T204.
- T205 puede construir encima de esta base para rate limiting y endurecimiento adicional de seguridad.
