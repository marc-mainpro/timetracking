# T501 — Gestión de empleados: casos de uso + API admin

- Iteración: 5 · Depende de: T303 · Contexto: CONTEXT-DOMINIO §1 (User), CONTEXT-API §2 (empleados)

## Objetivo
CRUD administrativo de empleados para `TENANT_ADMIN`.

## Detalle
1. `identity.application`: `CreateEmployee` (password inicial hasheada; evento `EmployeeCreated`), `UpdateEmployee` (nombre/apellidos), `ActivateEmployee`, `DeactivateEmployee` (evento `EmployeeDeactivated`; además revocar sus refresh tokens), `AssignRole` (validar que al menos un TENANT_ADMIN activo permanece en el tenant — no permitir que el tenant se quede sin admin, `errorCode: LAST_ADMIN`), `ListEmployees` (paginado, filtro por status), `GetEmployee`.
2. Todos requieren rol `TENANT_ADMIN` y operan solo sobre el tenant del contexto. Email duplicado en tenant → 409 `EMAIL_ALREADY_IN_USE`.
3. `interfaces.rest`: los 7 endpoints de CONTEXT-API §2 "Empleados". DTOs con Bean Validation; nunca exponer `passwordHash`.

## Pruebas
- Unitarias: cada caso de uso, incl. último-admin y email duplicado.
- Integración: CRUD completo por HTTP; empleado → 403 en todos; desactivado no puede loguear ni refrescar; casos añadidos a suite cross-tenant (admin A no gestiona empleados de B → 404).

## Fuera de alcance
Frontend (T502), auditoría de estas acciones (se añade en T603).

## Criterios de aceptación
- `mvn verify` verde; OpenAPI actualizado; suite T303 ampliada.

## Ficheros previstos
`identity/application/**`, `identity/interfaces/rest/EmployeeController.java`, tests.
