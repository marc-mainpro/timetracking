# Controles de autenticación

## Rate limiting

- Se aplica **Bucket4j en memoria** sobre:
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/register`
- Límite por IP: **10 solicitudes por minuto** en configuración normal.
- El exceso responde `429` con Problem Details y `errorCode = RATE_LIMIT_EXCEEDED`.
- La clave de limitación separa IP y ruta, de modo que un exceso en `login`
  no bloquea automáticamente `register` ni viceversa.
- No se usa almacenamiento distribuido en el MVP; un despliegue multiinstancia
  requerirá una estrategia compartida en una iteración futura.

## Cookies y tokens

- El access token JWT HS256 se devuelve solo en el body.
- El refresh token opaco se envía solo en cookie:
  `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`.
- El refresh token se persiste hasheado con SHA-256.
- La rotación es obligatoria y la reutilización invalida la cadena activa del
  usuario.

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
- La consulta `GET /api/v1/admin/audit-events` está restringida a
  `TENANT_ADMIN` y devuelve únicamente eventos del tenant autenticado.
