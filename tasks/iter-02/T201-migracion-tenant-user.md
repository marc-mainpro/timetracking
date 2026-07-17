# T201 — Migración de base de datos: tenant, user, refresh_token

- Iteración: 2 · Depende de: T103 · Contexto: CONTEXT-GLOBAL §5, CONTEXT-DOMINIO §1

## Objetivo
Crear las tablas de identidad con Flyway y sus tests de integración.

## Detalle
1. `V2__identity.sql`:
   - `tenant`: `id UUID PK`, `name VARCHAR(200) NOT NULL`, `status VARCHAR(20) NOT NULL`, `timezone VARCHAR(60) NOT NULL`, `created_at/updated_at TIMESTAMPTZ NOT NULL`.
   - `app_user`: `id UUID PK`, `tenant_id UUID NOT NULL FK→tenant`, `email VARCHAR(320) NOT NULL`, `password_hash VARCHAR(100) NOT NULL`, `first_name`, `last_name`, `status VARCHAR(20) NOT NULL`, `roles VARCHAR(100) NOT NULL` (CSV o tabla `user_role`; elegir tabla `user_role(user_id, role)` con PK compuesta), `created_at/updated_at`. **`UNIQUE (tenant_id, email)`**. Índice por `tenant_id`.
   - `refresh_token`: `id UUID PK`, `user_id UUID NOT NULL FK`, `token_hash VARCHAR(64) NOT NULL UNIQUE`, `expires_at TIMESTAMPTZ NOT NULL`, `revoked_at TIMESTAMPTZ`, `replaced_by UUID`, `created_at TIMESTAMPTZ NOT NULL`. Índice por `user_id`.
2. Test de integración con Testcontainers: Flyway aplica limpio desde cero; la unique `(tenant_id, email)` permite mismo email en tenants distintos y lo rechaza en el mismo.

## Fuera de alcance
Entidades JPA y lógica (T202+).

## Criterios de aceptación
- `mvn verify` verde; migración reproducible desde BD vacía.

## Ficheros previstos
`backend/src/main/resources/db/migration/V2__identity.sql`, test `FlywayIdentityMigrationIT.java`.
