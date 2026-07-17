# API

La especificación OpenAPI se genera a partir del código (springdoc-openapi)
en las tareas que implementan endpoints (a partir de la iteración 2). Se
publica en `/v3/api-docs` y `/swagger-ui.html` cuando la aplicación está en
marcha. Este documento se actualizará con el enlace/export de la
especificación cuando exista un pipeline que lo publique.

## Endpoints implementados

| Método | Ruta | Auth | Tarea |
|---|---|---|---|
| POST | `/api/v1/auth/register` | público | T203 |
| POST | `/api/v1/auth/login` | público | T204 |
| POST | `/api/v1/auth/refresh` | público | T204 |
| POST | `/api/v1/auth/logout` | Bearer JWT | T204 |
| GET | `/api/v1/workdays/current` | `EMPLOYEE` | T403 |
| POST | `/api/v1/workdays/start` | `EMPLOYEE` | T403 |
| POST | `/api/v1/workdays/current/breaks/start` | `EMPLOYEE` | T403 |
| POST | `/api/v1/workdays/current/breaks/end` | `EMPLOYEE` | T403 |
| POST | `/api/v1/workdays/current/end` | `EMPLOYEE` | T403 |
| GET | `/api/v1/workdays` | `EMPLOYEE` | T403 |
| GET | `/api/v1/workdays/{workdayId}` | `EMPLOYEE` | T403 |
| GET | `/api/v1/admin/workdays` | `TENANT_ADMIN` | T403 |
| GET | `/api/v1/admin/workdays/{workdayId}` | `TENANT_ADMIN` | T403 |

`POST /api/v1/auth/register`: crea un tenant y su primer usuario
`TENANT_ADMIN` de forma transaccional. Body: `tenantName`, `timezone`,
`adminEmail`, `adminPassword` (≥10 caracteres), `firstName`, `lastName`.
Responde `201` con `{tenantId, adminUserId}` (sin datos sensibles) y
`Location` apuntando al recurso tenant creado.

`POST /api/v1/auth/login`: autentica por `email + password`, verifica que el
usuario y su tenant estén activos, y devuelve `{accessToken, expiresAt}`.
Además emite una cookie `refresh_token` con `HttpOnly; Secure;
SameSite=Strict; Path=/api/v1/auth`. El endpoint está limitado por IP
(`10 req/min` en configuración normal). Si se supera, responde `429` con
`errorCode = RATE_LIMIT_EXCEEDED`.

`POST /api/v1/auth/refresh`: recibe la cookie `refresh_token`, la rota, emite
un nuevo access token y devuelve una nueva cookie refresh. La reutilización de
un refresh token ya rotado responde `401` con `errorCode = REFRESH_TOKEN_REUSED`
e invalida la cadena activa del usuario.

`POST /api/v1/auth/logout`: requiere Bearer JWT, revoca el refresh token de la
cookie actual si existe y devuelve `204` limpiando la cookie.

`POST /api/v1/auth/register`: también está limitado por IP (`10 req/min` en
configuración normal) y responde `429` con `RATE_LIMIT_EXCEEDED` cuando se
supera la ventana.

`GET /api/v1/workdays/current`: devuelve la jornada activa del empleado
autenticado o `404` si no existe.

`POST /api/v1/workdays/start`: abre una nueva jornada para el empleado
autenticado usando la hora del servidor. Responde `201` con la jornada creada.

`POST /api/v1/workdays/current/breaks/start` y
`POST /api/v1/workdays/current/breaks/end`: inician/finalizan la pausa abierta
de la jornada actual. Las transiciones inválidas responden `409` con el
`errorCode` de dominio correspondiente.

`POST /api/v1/workdays/current/end`: cierra la jornada actual. Si existe una
pausa abierta responde `409` con `WORKDAY_OPEN_BREAK`.

`GET /api/v1/workdays`: historial propio paginado (`page`, `size`, `from`,
`to`).

`GET /api/v1/workdays/{workdayId}`: devuelve la jornada solo si pertenece al
empleado autenticado; si no, `404`.

`GET /api/v1/admin/workdays` y `GET /api/v1/admin/workdays/{workdayId}`:
listado y detalle para `TENANT_ADMIN`, siempre acotados al tenant del
principal autenticado. Recursos de otro tenant responden `404`.

## Formato de error

Todas las respuestas de error de la API siguen RFC 7807 Problem Details:

```json
{
  "type": "about:blank",
  "title": "Invalid workday transition",
  "status": 409,
  "detail": "A workday cannot be closed while a break is open.",
  "errorCode": "WORKDAY_OPEN_BREAK",
  "correlationId": "uuid",
  "timestamp": "2026-07-17T12:00:00Z"
}
```

Sin stack traces ni detalles internos. `errorCode` estable y documentado.
Errores de validación incluyen detalle por campo
(`errors: [{field, message}]`). Conflictos de negocio o concurrencia
responden HTTP 409.

Si la request incluye cabecera `X-Correlation-Id`, ese mismo valor se
propaga a la respuesta y al campo `correlationId` de Problem Details. Si no,
el backend genera uno por request.

Errores de autenticación/sesión (`INVALID_CREDENTIALS`,
`INVALID_REFRESH_TOKEN`, `REFRESH_TOKEN_REUSED`, `USER_INACTIVE`,
`TENANT_INACTIVE`) responden HTTP 401.

Exceso de rate limit en endpoints públicos de autenticación responde HTTP 429
con `errorCode = RATE_LIMIT_EXCEEDED`.

## Roles

- `TENANT_ADMIN`: gestiona empleados, revisa correcciones, informes,
  auditoría.
- `EMPLOYEE`: su jornada, su historial, sus correcciones.
