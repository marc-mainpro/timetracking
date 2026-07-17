# ADR-0008: Email globalmente único para autenticación

* Estado: accepted
* Fecha: 2026-07-17

## Contexto y problema

El modelo inicial permitía reutilizar el mismo email en tenants distintos
(`UNIQUE (tenant_id, email)`). Al implementar `POST /api/v1/auth/login` con
`email + password`, esa decisión hacía imposible resolver de forma segura a
qué tenant autenticar cuando dos organizaciones compartían email.

## Decisión

* `app_user.email` pasa a ser **globalmente único**.
* Se añade una migración incremental que sustituye la restricción
  `UNIQUE (tenant_id, email)` por `UNIQUE (email)`.
* `RegisterTenant` valida duplicados a nivel global antes de crear el primer
  administrador.
* El login permanece como `email + password`; el `tenantId` viaja después en
  el JWT, resuelto desde el usuario autenticado.

## Consecuencias

* (+) El login deja de ser ambiguo y no necesita un identificador adicional de
  tenant.
* (+) Se mantiene la regla multitenant de no confiar en `tenant_id` enviado por
  el cliente.
* (-) Un mismo email ya no puede existir en dos tenants distintos dentro del
  MVP.
* (-) La regla general de incluir `tenant_id` en uniques tiene una excepción
  explícita y documentada por seguridad.
