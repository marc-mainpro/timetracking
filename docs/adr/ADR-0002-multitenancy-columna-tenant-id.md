# ADR-0002: Multitenancy por columna `tenant_id` con esquema compartido

* Estado: accepted
* Fecha: 2026-07-17

## Contexto y problema

El sistema debe aislar los datos de negocio de cada organización (tenant) de
forma estricta, con un coste operativo bajo (una única base de datos, un
único conjunto de migraciones).

## Decisión

Usar un **esquema compartido** con una columna `tenant_id` en toda tabla de
negocio. Toda restricción `UNIQUE` relevante incluye `tenant_id` (p. ej.
`UNIQUE (tenant_id, email)`). El tenant se resuelve siempre a partir del
usuario autenticado (`TenantContext`), nunca de un parámetro enviado por el
cliente. Toda query de negocio filtra por tenant; toda operación
administrativa comprueba rol y tenant. Toda tarea que toque datos mantiene
tests de acceso cruzado entre tenants.

## Alternativas consideradas

* Esquema por tenant (schema-per-tenant): mayor aislamiento, pero complica
  migraciones y conexión dinámica; descartado por complejidad operativa
  desproporcionada para un MVP.
* Base de datos por tenant: máximo aislamiento, coste operativo inasumible
  para el MVP; descartado.

## Consecuencias

* (+) Una sola base de datos y un solo pipeline de migraciones Flyway.
* (+) Consultas cruzadas de negocio (si se necesitaran) son triviales.
* (-) Riesgo de fuga de datos entre tenants si se omite el filtro; se mitiga
  con repositorios tenant-aware, `TenantContext` obligatorio y tests
  cross-tenant en toda tarea que toque datos.
