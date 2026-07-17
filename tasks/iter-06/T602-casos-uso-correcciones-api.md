# T602 — Casos de uso y API de correcciones

- Iteración: 6 · Depende de: T601 · Contexto: CONTEXT-DOMINIO, CONTEXT-API §2 (correcciones)

## Objetivo
Flujo completo solicitar → aprobar/rechazar corrección por API.

## Detalle
1. `corrections.application`: `RequestWorkdayCorrection` (EMPLOYEE, solo jornada propia → si no, 404; una PENDING por jornada/usuario → 409), `ApproveCorrectionRequest` (TENANT_ADMIN; transaccional: resuelve la solicitud + aplica `Workday.adjust` + registra auditoría vía puerto `AuditRecorder` de T603 — si T603 no está hecha aún, definir el puerto y una implementación no-op temporal claramente marcada), `RejectCorrectionRequest`, `ListCorrectionRequests` (EMPLOYEE: propias; ADMIN: todas las del tenant; filtro `?status=`), `GetCorrectionRequest` (mismas reglas de visibilidad).
2. `interfaces.rest`: los 5 endpoints de CONTEXT-API §2 "Correcciones".
3. Aprobación y jornada ajustada deben persistirse en la MISMA transacción; conflicto optimista → 409.

## Pruebas
- Unitarias por caso de uso (incl. visibilidad empleado/admin).
- Integración: flujo completo por HTTP; segunda resolución → 409; empleado no aprueba (403); corrección sobre jornada de otro usuario → 404; atomicidad aprobación+ajuste (forzar fallo tras resolver y verificar rollback); casos cross-tenant en suite T303.

## Criterios de aceptación
- `mvn verify` verde; OpenAPI actualizado.

## Ficheros previstos
`corrections/application/**`, `corrections/interfaces/rest/**`, tests.
