# API

La especificación OpenAPI se genera a partir del código (springdoc-openapi),
se expone en runtime en `/v3/api-docs` y `/v3/api-docs.yaml`, y se exporta al
repositorio como `docs/api/openapi.yaml`.

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
| GET | `/api/v1/employees` | `TENANT_ADMIN` | T501 |
| POST | `/api/v1/employees` | `TENANT_ADMIN` | T501 |
| GET | `/api/v1/employees/{employeeId}` | `TENANT_ADMIN` | T501 |
| PUT | `/api/v1/employees/{employeeId}` | `TENANT_ADMIN` | T501 |
| PATCH | `/api/v1/employees/{employeeId}/activate` | `TENANT_ADMIN` | T501 |
| PATCH | `/api/v1/employees/{employeeId}/deactivate` | `TENANT_ADMIN` | T501 |
| PUT | `/api/v1/employees/{employeeId}/roles` | `TENANT_ADMIN` | T501 |
| POST | `/api/v1/workdays/{workdayId}/corrections` | `EMPLOYEE` | T602 |
| GET | `/api/v1/corrections` | `EMPLOYEE` / `TENANT_ADMIN` | T602 |
| GET | `/api/v1/corrections/{correctionId}` | `EMPLOYEE` / `TENANT_ADMIN` | T602 |
| POST | `/api/v1/corrections/{correctionId}/approve` | `TENANT_ADMIN` | T602 |
| POST | `/api/v1/corrections/{correctionId}/reject` | `TENANT_ADMIN` | T602 |
| GET | `/api/v1/admin/audit-events` | `TENANT_ADMIN` | T603 |
| GET | `/api/v1/reports/employees/{employeeId}/summary` | `EMPLOYEE` / `TENANT_ADMIN` | T801 |
| GET | `/api/v1/reports/tenant/summary` | `TENANT_ADMIN` | T801 |
| GET | `/api/v1/reports/tenant/export.csv` | `TENANT_ADMIN` | T801 |

`POST /api/v1/auth/register`: crea un tenant y su primer usuario
`TENANT_ADMIN` de forma transaccional. Body: `tenantName`, `timezone`,
`adminEmail`, `adminPassword` (≥10 caracteres), `firstName`, `lastName`.
Responde `201` con `{tenantId, adminUserId}` (sin datos sensibles) y
`Location` apuntando al recurso tenant creado.

`POST /api/v1/auth/login`: autentica por `email + password`, verifica que el
usuario y su tenant estén activos, y devuelve `{accessToken, expiresAt}`.
Además emite una cookie `refresh_token` con `HttpOnly; SameSite=Strict;
Path=/api/v1/auth`; el flag `Secure` se activa en despliegues HTTPS y se puede
desactivar en entorno local para demo (`AUTH_REFRESH_COOKIE_SECURE=false`). El
endpoint está limitado por IP
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
supera la ventana. Si el email ya existe, el backend responde `409` sin
reflejar el correo original en el mensaje de error.

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

`GET /api/v1/employees`: listado paginado de empleados del tenant del admin,
con filtro opcional `status`.

`POST /api/v1/employees`: crea un empleado del tenant autenticado con password
inicial hasheada y roles explícitos.

`GET /api/v1/employees/{employeeId}` y `PUT /api/v1/employees/{employeeId}`:
detalle y actualización de nombre/apellidos. Si el empleado pertenece a otro
tenant responde `404`.

`PATCH /api/v1/employees/{employeeId}/activate` y
`PATCH /api/v1/employees/{employeeId}/deactivate`: activan o desactivan al
empleado. Desactivar revoca sus refresh tokens.

`PUT /api/v1/employees/{employeeId}/roles`: reemplaza los roles del empleado.
No se permite dejar al tenant sin ningún `TENANT_ADMIN` activo (`409`
`LAST_ADMIN`).

`POST /api/v1/workdays/{workdayId}/corrections`: crea una solicitud de
corrección sobre una jornada propia del empleado autenticado. Si la jornada no
es propia o no existe, responde `404`. Si ya existe una solicitud `PENDING`
del mismo solicitante para esa jornada, responde `409`
`CORRECTION_ALREADY_PENDING`.

`GET /api/v1/corrections`: listado paginado de correcciones. Un `EMPLOYEE`
solo ve las suyas; un `TENANT_ADMIN` ve todas las del tenant.

`GET /api/v1/corrections/{correctionId}`: devuelve el detalle si pertenece al
tenant y el actor tiene visibilidad; en caso contrario, `404`.

`POST /api/v1/corrections/{correctionId}/approve`: aprueba la corrección,
ajusta la jornada asociada y la deja en estado `ADJUSTED` en la misma
transacción.

`POST /api/v1/corrections/{correctionId}/reject`: rechaza la corrección. El
comentario de rechazo es obligatorio.

`GET /api/v1/admin/audit-events`: listado paginado de eventos de auditoría del
tenant autenticado, con filtros opcionales `action`, `from` y `to`. Solo está
disponible para `TENANT_ADMIN` y nunca expone registros de otros tenants.

`GET /api/v1/reports/employees/{employeeId}/summary`: resumen diario de
tiempo trabajado (`from`/`to` obligatorios, ISO-8601, rango máximo 366 días).
Los límites de día usan la zona horaria IANA del tenant (`Tenant.timezone`),
no UTC: una jornada que cruza medianoche local, o un día de cambio de hora
(23h/25h), se reparte entre los días que toca. Cada día devuelve `worked`
(trabajado, jornada menos pausas), `paused`, `workdayCount`,
`adjustedWorkdayCount` y `openWorkdays`. Las jornadas todavía abiertas se
excluyen de `worked`/`paused` (no hay forma fiable de saber cuánto trabajará
aún el empleado) pero se cuentan en `openWorkdays`. Un `EMPLOYEE` solo puede
pedir el suyo; un `TENANT_ADMIN` puede pedir el de cualquier empleado de su
tenant. Un `employeeId` de otro empleado (sin ser admin) o de otro tenant
responde `404`.

`GET /api/v1/reports/tenant/summary`: agregado por empleado en todo el rango
(mismos parámetros `from`/`to`/366 días), sin desglose diario. Solo
`TENANT_ADMIN`.

`GET /api/v1/reports/tenant/export.csv`: mismo dato que `tenant/summary` en
`text/csv` (UTF-8 sin BOM, cabecera `employeeId,workedSeconds,pausedSeconds,
workdayCount,adjustedWorkdayCount,openWorkdays`, campos escapados según RFC
4180). Solo `TENANT_ADMIN`.

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
