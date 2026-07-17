# ADR-0006: Problem Details (RFC 7807) para errores

* Estado: accepted
* Fecha: 2026-07-17

## Contexto y problema

La API necesita un formato de error uniforme, sin fugas de información
interna, que permita al frontend y a otros clientes distinguir errores de
negocio, validación y conflictos de concurrencia.

## Decisión

Todas las respuestas de error de la API usan **RFC 7807 Problem Details**:

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

Sin stack traces ni detalles internos. `errorCode` es estable y está
documentado (ver `docs/domain/reglas-de-negocio.md`). Los errores de
validación incluyen detalle por campo (`errors: [{field, message}]`). Los
conflictos de negocio o de concurrencia optimista responden HTTP 409.

## Consecuencias

* (+) Formato de error estándar, fácil de consumir desde Angular.
* (+) `errorCode` estable permite manejo programático en el frontend sin
  parsear texto.
* (+) No se filtran detalles internos (stack traces, mensajes de
  excepciones de infraestructura).
* (-) Requiere mantener un mapeo centralizado `DomainException` →
  `errorCode` → HTTP status, que debe actualizarse en cada tarea que añada
  una nueva excepción de dominio.
