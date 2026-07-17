# API

La especificación OpenAPI se genera a partir del código (springdoc-openapi)
en las tareas que implementan endpoints (a partir de la iteración 2). Este
documento se actualizará con el enlace/export de la especificación cuando
exista.

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

## Roles

- `TENANT_ADMIN`: gestiona empleados, revisa correcciones, informes,
  auditoría.
- `EMPLOYEE`: su jornada, su historial, sus correcciones.
