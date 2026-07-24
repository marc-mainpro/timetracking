# ADR-0010: Ciclo de vida del tenant y administración de plataforma

* Estado: accepted
* Fecha: 2026-07-24

## Contexto y problema

La V2 requiere administrar tenants de forma segura (requisitos RF-TEN-001..010):
un administrador global debe poder listar tenants y gobernar su ciclo de vida
(pendiente → activo → suspendido → archivado), y un tenant no operativo no debe
poder trabajar. El MVP solo modelaba `ACTIVE`/`INACTIVE` y solo tenía los roles
`TENANT_ADMIN`/`EMPLOYEE`, sin ninguna superficie de administración transversal
a todos los tenants.

## Decisión

1. **Ciclo de vida del tenant.** El agregado `Tenant` pasa a modelar los estados
   `PENDING`, `ACTIVE`, `SUSPENDED`, `ARCHIVED` (migración V10, con `CHECK` y
   marcas de tiempo `activated_at`/`suspended_at`/`archived_at` + motivo de
   suspensión). Las transiciones válidas (diseño §7.2) son
   `PENDING→ACTIVE`, `ACTIVE→SUSPENDED`, `SUSPENDED→ACTIVE`,
   `ACTIVE→ARCHIVED`, `SUSPENDED→ARCHIVED`; `ARCHIVED` es terminal. Una
   transición inválida es una violación de invariante (`IllegalTenantTransitionException`
   → 409). Solo `ACTIVE` permite operar; el bloqueo por estado ya lo aplican
   login, refresh y la comprobación por petición vía `TenantAccessRepository`
   (`status = 'ACTIVE'`).

2. **Rol `PLATFORM_ADMIN`.** Nuevo rol global, **no asignable dentro de un
   tenant** (`Role.tenantAssignableRoles` lo rechaza en alta de empleados y
   cambio de roles) ni por registro público. Se aprovisiona de forma
   controlada e idempotente (`PlatformAdminBootstrap`) a partir de
   `PLATFORM_ADMIN_EMAIL`/`PLATFORM_ADMIN_PASSWORD` (secreto por entorno,
   nunca en el repositorio).

3. **Tenant de sistema.** Los usuarios `PLATFORM_ADMIN` pertenecen a un tenant
   de sistema de id fijo (`shared.domain.PlatformTenant.ID`, creado por la
   migración V11), reutilizando el modelo de autenticación y de `TenantContext`
   existentes en vez de introducir un modelo de identidad paralelo. Ese tenant
   se excluye del listado de plataforma y no opera con datos de negocio.

4. **API de plataforma.** Vive **dentro del módulo `tenant`** (que posee el
   agregado y su repositorio), exponiendo `/api/v1/platform/tenants` (listado
   paginado + filtro, detalle y transiciones) protegido con
   `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`. Cada transición publica el
   evento de integración correspondiente (`tenant.*.v1`) vía Outbox y registra
   auditoría de plataforma (estado anterior/posterior + motivo). La consulta de
   auditoría de plataforma (`/api/v1/platform/audit`) reutiliza
   `ListAuditEventsUseCase`, que queda naturalmente acotado al tenant de sistema
   para un `PLATFORM_ADMIN`.

## Consecuencias

* (+) Administración de tenants segura y auditable sin duplicar el modelo de
  identidad ni la infraestructura de autenticación.
* (+) El bloqueo por estado es transversal (login/refresh/por petición) porque
  se apoya en el puerto ya existente `TenantAccessRepository`.
* (+) La API de plataforma no rompe la modularidad: no accede a repositorios de
  otros módulos (solo al del propio `tenant` y a puertos de aplicación como
  `AuditRecorder`).
* (−) El tenant de sistema es un caso especial que debe recordarse excluir en
  cualquier consulta transversal de tenants (encapsulado en los casos de uso de
  plataforma).
* (−) `PLATFORM_ADMIN` viaja en el mismo JWT que los roles de negocio; se evita
  su escalada prohibiendo su asignación en el ámbito de tenant.

## Alternativas descartadas

* **Modelo de identidad separado para la plataforma** (tabla/tabla de auth
  propia): más aislamiento, pero duplica autenticación, refresh y auditoría sin
  necesidad real en esta escala.
* **Módulo `platform` independiente** que dependa del repositorio de `tenant`:
  rompería la regla de no acceder al repositorio de otro módulo (ADR-0001); la
  administración vive donde vive el agregado.
