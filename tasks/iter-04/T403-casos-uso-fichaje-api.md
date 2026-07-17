# T403 — Casos de uso de fichaje + API de jornadas

- Iteración: 4 · Depende de: T402, T301 · Contexto: CONTEXT-DOMINIO, CONTEXT-API §2 (jornadas y admin)

## Objetivo
Exponer el ciclo completo de fichaje por API para el empleado autenticado, y consulta admin.

## Detalle
1. `timetracking.application`: `StartWorkday`, `StartBreak`, `EndBreak`, `EndWorkday`, `GetCurrentWorkday`, `ListOwnWorkdays`, `GetWorkday`, `ListTenantWorkdays`. Identidad y tenant SIEMPRE de `TenantContext`; hora del `Clock` del servidor; eventos de dominio al `DomainEventPublisher`; tenant inactivo → rechazo.
2. `ListTenantWorkdays`/`GetWorkday` versión admin: comprobar rol `TENANT_ADMIN` (`@PreAuthorize`) y tenant.
3. `interfaces.rest`: endpoints de CONTEXT-API §2 "Jornadas" y "Administración de jornadas". DTO `WorkdayResponse` (id, status, startedAt, endedAt, breaks[], duración trabajada calculada). 404 si `current` no existe; 409 en transiciones inválidas; mapear `OptimisticLockException` → 409 `CONCURRENT_MODIFICATION`.

## Pruebas
- Unitarias de casos de uso: feliz + cada invariante violada + jornada ya abierta.
- Integración: flujo start→break start→break end→end por HTTP; transiciones inválidas → 409 con `errorCode` correcto; empleado no accede a `/admin/workdays` (403); casos cross-tenant añadidos a la suite T303 (admin de A no ve jornadas de B).

## Fuera de alcance
Frontend (T404), correcciones (iter. 6).

## Criterios de aceptación
- `mvn verify` verde; OpenAPI actualizado; suite cross-tenant ampliada.

## Ficheros previstos
`timetracking/application/**`, `timetracking/interfaces/rest/**`, tests, `docs/api/`.
