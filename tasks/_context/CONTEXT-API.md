# CONTEXT-API — Contratos REST y convenciones

Leer junto a CONTEXT-GLOBAL en toda tarea que exponga o consuma la API.

## 1. Convenciones

- Prefijo `/api/v1`. DTOs propios (request/response) con Bean Validation; nunca entidades JPA ni objetos de dominio directamente.
- Autenticación por `Authorization: Bearer <access_token>` salvo `/auth/register`, `/auth/login`, `/auth/refresh`.
- Refresh token viaja SOLO por cookie HttpOnly. Errores según Problem Details (CONTEXT-GLOBAL §7).
- Paginación en listados: `page` (0-based), `size` (def. 20, máx. 100); respuesta `{content, page, size, totalElements, totalPages}`.
- Toda ruta nueva queda reflejada en OpenAPI (springdoc) y en `docs/api/`.

## 2. Endpoints

### Autenticación (públicos, con rate limiting)
```text
POST /api/v1/auth/register   # crea tenant + primer TENANT_ADMIN. Body: tenantName, timezone, adminEmail, adminPassword, firstName, lastName
POST /api/v1/auth/login      # email+password → access token (body) + refresh cookie
POST /api/v1/auth/refresh    # rota refresh cookie → nuevo access token
POST /api/v1/auth/logout     # invalida refresh token, borra cookie
```

### Empleados (rol TENANT_ADMIN)
```text
GET    /api/v1/employees                          # paginado, filtro ?status=
POST   /api/v1/employees                          # email, firstName, lastName, password inicial, roles
GET    /api/v1/employees/{employeeId}
PUT    /api/v1/employees/{employeeId}             # firstName, lastName
PATCH  /api/v1/employees/{employeeId}/activate
PATCH  /api/v1/employees/{employeeId}/deactivate
PUT    /api/v1/employees/{employeeId}/roles       # lista de roles
```

### Jornadas (rol EMPLOYEE; operan sobre el usuario autenticado)
```text
GET  /api/v1/workdays/current                # 404 si no hay jornada activa
POST /api/v1/workdays/start
POST /api/v1/workdays/current/breaks/start
POST /api/v1/workdays/current/breaks/end
POST /api/v1/workdays/current/end
GET  /api/v1/workdays                        # historial propio, paginado, filtros ?from=&to=
GET  /api/v1/workdays/{workdayId}            # solo si es propia (o admin por ruta admin)
```

### Administración de jornadas (rol TENANT_ADMIN)
```text
GET /api/v1/admin/workdays                   # todas las del tenant, filtros ?employeeId=&from=&to=
GET /api/v1/admin/workdays/{workdayId}
```

### Correcciones
```text
POST /api/v1/workdays/{workdayId}/corrections    # EMPLOYEE, sobre jornada propia. Body: reason, proposedChanges
GET  /api/v1/corrections                         # EMPLOYEE: las suyas; TENANT_ADMIN: todas las del tenant. ?status=
GET  /api/v1/corrections/{correctionId}
POST /api/v1/corrections/{correctionId}/approve  # TENANT_ADMIN. Body: resolutionComment?
POST /api/v1/corrections/{correctionId}/reject   # TENANT_ADMIN. Body: resolutionComment
```

### Informes (rol TENANT_ADMIN; summary propio también para EMPLOYEE)
```text
GET /api/v1/reports/employees/{employeeId}/summary   # ?from=&to= → total trabajado, pausas, nº jornadas por día
GET /api/v1/reports/tenant/summary                   # ?from=&to= → agregado por empleado
GET /api/v1/reports/tenant/export.csv                # mismo filtro, text/csv
```

## 3. Códigos de estado

- 200/201 éxito (201 + `Location` en creaciones), 204 sin cuerpo.
- 400 validación, 401 no autenticado, 403 rol/tenant incorrecto, 404 no existe **o pertenece a otro tenant** (no revelar existencia), 409 conflicto de negocio o de concurrencia, 429 rate limit.

## 4. Frontend

- Rutas con guard de autenticación y guard de rol (`TENANT_ADMIN` para admin).
- Interceptor añade Bearer y, en 401, intenta refresh una vez y reintenta; si falla → logout + redirección a login.
- Manejo global de errores: mapear Problem Details a mensajes claros; estados de carga en toda llamada.
- Formularios reactivos con validación cliente espejo de la del servidor.
