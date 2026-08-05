# Controles de autenticación

## Rate limiting

Ampliado en T30-03 (RS-007). Ver ADR-0014.

- Se aplica **Bucket4j en memoria**. Las reglas ya no están en el código: son
  configuración por **patrón de ruta** en `config/account-lockout.yml`
  (`auth.rate-limit.endpoints[]`), lo que permite ajustar límites sin
  recompilar y cubrir endpoints antes de que existan.

  | Patrón | Método | Límite |
  |---|---|---|
  | `/api/v1/auth/login` | POST | 10 / min (valor global heredado) |
  | `/api/v1/auth/register` | POST | 10 / min (valor global heredado) |
  | `/api/v1/auth/refresh` | POST | 30 / min |
  | `/api/v1/auth/password/**` | POST | 5 / 15 min |
  | `/api/v1/auth/verification/**` | POST | 5 / 15 min |

- `capacity`/`window` son opcionales por regla; cuando faltan se heredan de
  `auth.rate-limit.capacity` y `auth.rate-limit.window` (`application.yml`).
- `refresh` lleva un límite más alto porque es una operación legítima y
  frecuente y varios usuarios tras el mismo NAT comparten IP de origen.
- Recuperación de contraseña y reenvío de verificación llevan el límite más
  estricto: cada petición envía un correo, así que el abuso incluye usar el
  sistema para bombardear un buzón ajeno.
- El exceso responde `429` con Problem Details y `errorCode = RATE_LIMIT_EXCEEDED`.
- La clave de limitación combina IP y regla, de modo que un exceso en `login`
  no consume la cuota de `refresh` ni viceversa.
- Sin ninguna regla configurada el filtro cae al mínimo histórico (login y
  registro) en lugar de dejar de limitar.
- No se usa almacenamiento distribuido: un despliegue multiinstancia requerirá
  un limitador en el borde (riesgo residual documentado en `threat-model.md` y
  `owasp-review.md`).

## Bloqueo temporal de cuentas

T30-04 (RF-USR-008, RS-008). Ver ADR-0014.

- Estado persistido en la tabla `account_lockout` (agregado propio
  `AccountLockout`, no dentro de `User`): intentos fallidos, fecha del último
  intento y fecha de desbloqueo.
- Configurable en `config/account-lockout.yml`: `auth.account-lockout.threshold`
  (5), `auth.account-lockout.lock-duration` (PT15M) y
  `auth.account-lockout.failure-window` (PT30M, tras la cual un fallo aislado
  deja de contar).
- El contador se reinicia con cada autenticación correcta.
- El bloqueo expira solo y **no se prolonga** con los intentos posteriores, para
  que no pueda usarse como denegación de servicio contra un tercero.
- **Anti-enumeración**: con credenciales incorrectas la respuesta es siempre
  `401 INVALID_CREDENTIALS`, indistinguible entre cuenta bloqueada, cuenta
  existente y email inexistente. `401 ACCOUNT_LOCKED` solo se devuelve a quien
  además ha acertado la contraseña, es decir, al titular de la cuenta.
- Complementa al rate limiting: este mira la IP y aquel la cuenta, así que el
  bloqueo es el que frena el ataque distribuido entre muchas IPs y el único que
  sigue vigente con varias instancias.
- Métricas expuestas por Actuator: `auth.login.failed{reason}`,
  `auth.login.succeeded`, `auth.accounts.locked`.

## Cookies y tokens

- El access token JWT HS256 se devuelve solo en el body.
- El refresh token opaco se envía solo en cookie:
  `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`.
- El refresh token se persiste hasheado con SHA-256.
- La rotación es obligatoria y la reutilización invalida la cadena activa del
  usuario.
- La recuperación de contraseña usa un token aleatorio de un solo uso,
  persistido solo como hash SHA-256 en `password_reset_token`.
- Confirmar el reset revoca todos los `refresh_token` del usuario para que las
  sesiones previas no sigan vivas con la contrasena antigua.

## CSRF

- La API funciona en modo stateless con Bearer JWT para recursos protegidos.
- `CSRF` se mantiene deshabilitado en Spring Security porque no existe sesión
  de servidor ni autenticación por cookie para las operaciones de negocio.
- El refresh token sigue viajando en cookie, pero el riesgo se reduce con:
  - `SameSite=Strict`
  - `HttpOnly`
  - `Secure`
  - refresh limitado a `/api/v1/auth`

## Cabeceras HTTP

- Se mantienen las cabeceras de seguridad por defecto de Spring Security.
- Los endpoints de autenticación devuelven además respuestas marcadas como
  no cacheables (`Cache-Control: no-store`, `Pragma: no-cache`).

## Auditoría

- La auditoría es append-only en `audit_event`; la aplicación no expone
  endpoints de escritura, edición ni borrado.
- Cada registro toma `tenantId`, `actorUserId` y `correlationId` del contexto
  autenticado y de la request actual; no se aceptan desde el cliente.
- `metadata` excluye secretos, credenciales, refresh tokens y access tokens.
- Acciones auditadas en `T603`:
  - creación de empleado (`EMPLOYEE_CREATED`)
  - activación de empleado (`EMPLOYEE_ACTIVATED`)
  - desactivación de empleado (`EMPLOYEE_DEACTIVATED`)
  - cambio de roles (`EMPLOYEE_ROLES_UPDATED`)
  - aprobación de corrección (`CORRECTION_APPROVED`)
  - rechazo de corrección (`CORRECTION_REJECTED`)
- Acciones auditadas en `T30-04` (bloqueo temporal de cuentas). Ocurren antes de
  que exista principal autenticado, así que `tenantId` y `actorUserId` los
  aporta el caso de uso a partir del usuario resuelto por email, nunca el
  cliente:
  - intento de login fallido (`LOGIN_FAILED`)
  - bloqueo de la cuenta al alcanzar el umbral (`ACCOUNT_LOCKED`)
  - intento contra una cuenta ya bloqueada (`LOGIN_ATTEMPT_WHILE_LOCKED`)
- La consulta `GET /api/v1/admin/audit-events` está restringida a
  `TENANT_ADMIN` y devuelve únicamente eventos del tenant autenticado.
