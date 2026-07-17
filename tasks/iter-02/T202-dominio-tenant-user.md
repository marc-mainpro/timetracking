# T202 — Dominio Tenant y User + persistencia

- Iteración: 2 · Depende de: T201 · Contexto: CONTEXT-GLOBAL §3-4, CONTEXT-DOMINIO §1-3

## Objetivo
Modelar Tenant y User como agregados de dominio puros (sin Spring/JPA), sus puertos de repositorio y los adaptadores JPA.

## Detalle
1. `tenant.domain`: agregado `Tenant` (factoría `register(name, timezone)` valida nombre obligatorio y timezone IANA con `ZoneId`; métodos `deactivate()`; `isActive()`), evento `TenantRegistered`. Puerto `TenantRepository` (interfaz en dominio).
2. `identity.domain`: agregado `User` (factoría `create(...)` — email válido y normalizado a minúsculas, roles no vacíos; métodos `activate()/deactivate()`, `assignRoles(...)`; regla: usuario inactivo no autentica), VO `Email`, enum `Role {TENANT_ADMIN, EMPLOYEE}`, eventos `EmployeeCreated`, `EmployeeDeactivated`. Puerto `UserRepository` con `findByTenantIdAndEmail`, `existsByTenantIdAndEmail`, etc.
3. `infrastructure.persistence` de cada módulo: entidades JPA separadas (`TenantJpaEntity`, `UserJpaEntity`) + mappers dominio↔JPA + adaptadores Spring Data implementando los puertos.
4. Los agregados acumulan eventos de dominio (`pullDomainEvents()`), según CONTEXT-DOMINIO §3.

## Pruebas
- Unitarias de dominio: validaciones de factoría, transiciones de estado, eventos generados, email inválido, timezone inválida (objetivo ≥90 % en estos paquetes).
- Integración: adaptadores JPA persisten y recuperan; mapeo completo; unique por tenant.

## Fuera de alcance
Casos de uso y endpoints (T203, T204), hashing de password (el dominio recibe `passwordHash` ya calculado).

## Criterios de aceptación
- ArchUnit verde (dominio sin Spring/JPA); `mvn verify` verde.

## Ficheros previstos
`backend/src/main/java/com/tfp/timetracking/{tenant,identity}/{domain,infrastructure}/**`, tests correspondientes.
