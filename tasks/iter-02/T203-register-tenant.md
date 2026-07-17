# T203 — Caso de uso RegisterTenant + endpoint

- Iteración: 2 · Depende de: T202 · Contexto: CONTEXT-GLOBAL, CONTEXT-DOMINIO §1-3, CONTEXT-API (§2 auth)

## Objetivo
Registro de organización: crea Tenant + primer usuario TENANT_ADMIN de forma transaccional.

## Detalle
1. `tenant.application`: caso de uso `RegisterTenant` (command: tenantName, timezone, adminEmail, adminPassword, firstName, lastName). Flujo: valida, crea `Tenant`, hashea password (puerto `PasswordHasher`, implementación BCrypt en infraestructura), crea `User` admin, persiste ambos en una transacción (la transaccionalidad se aplica en un adaptador/configuración de application, no en el dominio), recoge eventos `TenantRegistered` y `EmployeeCreated` y los entrega al puerto `DomainEventPublisher` (implementación provisional: log + hook para outbox en T702).
2. `interfaces.rest`: `POST /api/v1/auth/register` (público). DTO request con Bean Validation (password ≥ 10 chars), respuesta 201 con ids (sin datos sensibles).
3. Manejador global de errores (`@RestControllerAdvice`) con Problem Details según CONTEXT-GLOBAL §7 — crearlo en `shared.interfaces` si no existe, mapeando `DomainException`→409/400, validación→400 con errores por campo.

## Pruebas
- Unitarias del caso de uso (Mockito): éxito, timezone inválida, email duplicado en tenant (nota: dos tenants pueden repetir email).
- Integración (Testcontainers + MockMvc): 201 feliz, 400 validación con detalle por campo, atomicidad (si falla la creación del user no queda tenant huérfano).

## Fuera de alcance
Login/refresh (T204), rate limiting (T205), outbox real.

## Criterios de aceptación
- `mvn verify` verde; OpenAPI expone el endpoint; errores en formato Problem Details.

## Ficheros previstos
`tenant/application/**`, `tenant/interfaces/rest/**`, `shared/interfaces/rest/GlobalExceptionHandler.java`, tests.
