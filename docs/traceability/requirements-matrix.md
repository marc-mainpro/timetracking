# Matriz de trazabilidad — V2 (Iteración 1: administración de tenants)

Relaciona requisito → tarea → caso de uso / componente → endpoint → prueba.
Cubre la iteración de administración de tenants (épicas T50, T130 parcial,
T00). Se ampliará en iteraciones posteriores (registro público controlado,
sesiones, calendarios, ausencias, turnos, informes, notificaciones).

## Administración de tenants (RF-TEN)

| Requisito | Tarea | Caso de uso / componente | Endpoint | Pruebas |
|---|---|---|---|---|
| RF-TEN-001 Listado de tenants | T50-05 | `ListTenantsUseCase`, `PlatformTenantController.list` | `GET /api/v1/platform/tenants` | `ListTenantsUseCaseTest`, `PlatformTenantControllerIntegrationTest` |
| RF-TEN-002 Consulta de tenant | T50-05 | `GetTenantUseCase`, `PlatformTenantController.get` | `GET /api/v1/platform/tenants/{id}` | `GetTenantUseCaseTest`, `PlatformTenantControllerIntegrationTest` |
| RF-TEN-003 Creación por PLATFORM_ADMIN | T50-05 | `CreateTenantUseCase`, `PlatformTenantController.create` | `POST /api/v1/platform/tenants` | `CreateTenantUseCaseTest`, `PlatformTenantControllerIntegrationTest` |
| RF-TEN-010 Registro público deshabilitable | T53-04 | `AuthRegisterController` + flag `registration.public.enabled` | `POST /api/v1/auth/register` (403 si off) | `PublicRegistrationDisabledIntegrationTest` |
| RF-TEN-004 Ciclo de vida | T50-01, T50-02 | `Tenant` (estados + transiciones), migración V10 | — | `TenantTest`, `TenantRepositoryAdapterIntegrationTest` |
| RF-TEN-005 Activación | T50-05 | `ChangeTenantLifecycleUseCase.activate` | `POST /api/v1/platform/tenants/{id}/activate` | `ChangeTenantLifecycleUseCaseTest`, `PlatformTenantControllerIntegrationTest` |
| RF-TEN-006 Suspensión (motivo) | T50-05 | `ChangeTenantLifecycleUseCase.suspend` | `POST /api/v1/platform/tenants/{id}/suspend` | `ChangeTenantLifecycleUseCaseTest`, `PlatformTenantControllerIntegrationTest` |
| RF-TEN-007 Reactivación | T50-05 | `ChangeTenantLifecycleUseCase.reactivate` | `POST /api/v1/platform/tenants/{id}/reactivate` | `ChangeTenantLifecycleUseCaseTest`, `PlatformTenantControllerIntegrationTest` |
| RF-TEN-008 Archivado | T50-05 | `ChangeTenantLifecycleUseCase.archive` | `POST /api/v1/platform/tenants/{id}/archive` | `ChangeTenantLifecycleUseCaseTest` |
| RF-TEN-009 Bloqueo por estado | T50-03 | `AuthenticateUserUseCase`, `RefreshSessionUseCase`, `IdentityAuthenticatedPrincipalStateChecker`, `TenantAccessRepository` | login/refresh + toda petición autenticada | `TenantAccessRepositoryAdapterIntegrationTest`, `AuthSecurityIntegrationTest`, `PlatformTenantControllerIntegrationTest` |

## Rol y seguridad de plataforma

| Requisito | Tarea | Caso de uso / componente | Prueba |
|---|---|---|---|
| Actor PLATFORM_ADMIN (RF §4.1) | T50-04 | `Role.PLATFORM_ADMIN`, `PlatformAdminBootstrap`, `PlatformTenant` | `RoleTest`, `PlatformAdminBootstrapTest`, `PlatformTenantTest` |
| PLATFORM_ADMIN no asignable en tenant (RS-010) | T50-04 | `Role.tenantAssignableRoles`, `PlatformRoleNotAssignableException` | `RoleTest` |
| Autorización por rol en plataforma (RS-010) | T50-05 | `@PreAuthorize('hasRole(PLATFORM_ADMIN)')` | `PlatformTenantControllerIntegrationTest` (TENANT_ADMIN → 403) |

## Auditoría de plataforma (RF-AUD)

| Requisito | Tarea | Caso de uso / componente | Endpoint | Prueba |
|---|---|---|---|---|
| RF-AUD-001 Auditoría de plataforma | T130-03 | `ChangeTenantLifecycleUseCase` (registra), `PlatformAuditController` | `GET /api/v1/platform/audit` | `ChangeTenantLifecycleUseCaseTest`, `PlatformTenantControllerIntegrationTest` |
| RF-AUD-003 Contenido (estado anterior/posterior, motivo) | T130-03 | metadata de `AuditRecorder` | — | `ChangeTenantLifecycleUseCaseTest` |

## Frontend de plataforma

| Requisito | Tarea | Componente | Prueba |
|---|---|---|---|
| Panel de plataforma (RF-TEN-001..008 UI) | T50-06 | `PlatformTenantsComponent`, `PlatformTenantsService`, `roleGuard(['PLATFORM_ADMIN'])` | `platform-tenants.service.spec`, `platform-tenants.component.spec`, `auth.guard.spec` |

## Documentación

| Artefacto | Tarea | Ubicación |
|---|---|---|
| ADR ciclo de vida + plataforma | — | `docs/adr/ADR-0010-...md` |
| Catálogo de eventos `tenant.*.v1` | — | `docs/integration/event-catalog.md` |
| OpenAPI `/api/v1/platform/**` | — | `docs/api/openapi.yaml` |
| Reglas del agente V2 | T00-03 | `AGENTS.md` |

## Seguridad de la cadena de suministro (T30-05)

| Requisito | Tarea | Componente | Evidencia |
|---|---|---|---|
| RS-015 Análisis de dependencias vulnerables | T30-05 | Job `frontend-dependencies` (`npm audit` + gate) y job `backend-dependencies` (OWASP dependency-check 13.0.0) de `.github/workflows/ci.yml` | `scripts/security/npm-audit-gate.sh` (probado: 24 advisories high/critical detectados y contrastados con la allowlist), `scripts/security/npm-audit-allowlist.txt`, `backend/dependency-check-suppressions.xml`. **Parcial en backend**: requiere el secreto `NVD_API_KEY` (ver `docs/security/dependency-scanning-policy.md` §4) |
| RS-016 Detección de secretos | T30-05 | Job `secret-scan` de `.github/workflows/ci.yml` (gitleaks 8.30.1, `fetch-depth: 0`, checksum del binario verificado) | `.gitleaks.toml`; escaneo de 124 commits con 0 hallazgos; prueba negativa: una clave AWS de ejemplo inyectada en `.env.example` sí se detecta |
| Política de severidad y excepciones (T30-05) | T30-05 | Umbral *high* en ambos escáneres; excepciones nominales con fecha de caducidad y aprobación por rol | `docs/security/dependency-scanning-policy.md` |

## Operación: backups y restauración (T150)

| Requisito | Tarea | Componente | Evidencia |
|---|---|---|---|
| RO-005 Backups periódicos de PostgreSQL | T150-02 | `scripts/backup/backup-postgres.sh` (`pg_dump -Fc`, cifrado AES-256, SHA-256, logs, códigos de salida) + cron diario 03:00 | Ejecución real 2026-08-04: backup de 24 011 B verificado con `pg_restore --list`; `docs/manuals/backup-restore.md` §2 |
| RO-006 Política de retención | T150-01, T150-02 | Rotación *grandfather-father-son* en `backup-postgres.sh` (7 días + 4 semanas ISO) | `docs/adr/ADR-0013-estrategia-backup-retencion.md`; rotación probada con 9 backups sintéticos: elimina los que caen fuera de ambas ventanas y conserva el primero de cada semana ISO |
| RO-007 Procedimiento de restauración documentado y probado | T150-03, T150-04 | `scripts/backup/restore-postgres.sh` (parar → restaurar → validar → arrancar) + `scripts/smoke.sh` | `docs/manuals/backup-restore.md` §3 y §4; simulacro ejecutado 2026-08-04 |
| RT-008 Restauración validada y documentada | T150-04 | Validación automática en el script (esquema, Flyway sin fallos, tablas de negocio con filas) y smoke tests | Simulacro 2026-08-04: 13 s, `TRUNCATE tenant CASCADE` recuperado a 3 tenants / 5 usuarios / 8 outbox, login HTTP 200, `scripts/smoke.sh` en verde. Acta completa en `docs/manuals/backup-restore.md` §4 |
| RC-008 Sin alta disponibilidad (restricción asumida) | T150-01 | Backup lógico diario en lugar de PITR/réplica; RPO 24 h, RTO 1 h declarados | `docs/adr/ADR-0013-estrategia-backup-retencion.md` (alternativas descartadas) |

## Pendiente (fuera de esta iteración)

- T53 Registro público controlado (TenantRegistration, verificación de correo,
  feature flag).
- T130-04/05 Auditoría de tenant ampliada y consulta avanzada.
- RF-TEN-001: `número de usuarios` y `último acceso` en el listado (dependen de
  identity/sesiones — épica T60).
