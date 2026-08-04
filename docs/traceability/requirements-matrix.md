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

## Seguridad de autenticación (RS-007, RS-008, RF-USR-008)

| Requisito | Tarea | Caso de uso / componente | Endpoint | Prueba |
|---|---|---|---|---|
| RF-USR-008 Bloqueo temporal tras intentos fallidos | T30-04 | `AccountLockout` (agregado), `AccountLockoutService`, `AuthenticateUserUseCase` | `POST /api/v1/auth/login` (401 `ACCOUNT_LOCKED`) | `AccountLockoutTest`, `AccountLockoutServiceTest`, `AccountLockoutIntegrationTest` |
| RS-007 Rate limiting en endpoints sensibles | T30-03 | `RateLimitFilter`, `RateLimitProperties`, `config/account-lockout.yml` | `POST /api/v1/auth/{login,register,refresh}`, `/password/**`, `/verification/**` (429 `RATE_LIMIT_EXCEEDED`) | `RateLimitPropertiesTest`, `RateLimitFilterIntegrationTest`, `AuthSecurityIntegrationTest` |
| RS-008 Umbral y duración configurables | T30-04 | `AccountLockoutPolicy` (`auth.account-lockout.*`) | — | `AccountLockoutServiceTest`, `AccountLockoutIntegrationTest` |
| RS-008 Reinicio tras autenticación correcta | T30-04 | `AccountLockout.registerSuccess`, `AccountLockoutService.registerSuccessfulAttempt` | — | `AccountLockoutTest`, `AccountLockoutIntegrationTest#aSuccessfulLoginResetsTheFailureCounter` |
| RS-008 Auditoría del bloqueo | T30-04 | `AuditRecorder` con tenant/actor explícitos (`LOGIN_FAILED`, `ACCOUNT_LOCKED`, `LOGIN_ATTEMPT_WHILE_LOCKED`) | — | `AccountLockoutIntegrationTest#auditOfAFailedLoginIsRecordedUnderTheTenantOfTheAccount` |
| RS-008 Anti-enumeración del bloqueo | T30-04 | `AuthenticateUserUseCase` (contraseña comprobada antes de decidir la respuesta) | `POST /api/v1/auth/login` | `AccountLockoutIntegrationTest#lockedAccountIsIndistinguishableFromAnUnknownAccount` |
| RS-007/RS-008 Aislamiento entre tenants (RT-003) | T30-04 | `AccountLockoutRepository` tenant-scoped | — | `AccountLockoutIntegrationTest#lockingAnAccountDoesNotAffectAccountsOfAnotherTenant` |
| RS-008 Aplicación por rol (RT-004) | T30-04 | `AccountLockoutService` | — | `AccountLockoutIntegrationTest#lockoutAppliesToTenantAdminsToo` |
| T140 Métricas de autenticación | T30-04 | `AuthenticationMetrics` (`auth.login.failed`, `auth.login.succeeded`, `auth.accounts.locked`) | `GET /actuator/metrics` | `AccountLockoutIntegrationTest#failedAndLockedCountersAreExposedAsMetrics` |
| RS-007/RS-008 Mensaje al usuario | T30-03/04 | `ErrorMessagesService` (`ACCOUNT_LOCKED`, `RATE_LIMIT_EXCEEDED`) | — | `error-messages.service.spec`, `login.component.spec` |

## Frontend de plataforma

| Requisito | Tarea | Componente | Prueba |
|---|---|---|---|
| Panel de plataforma (RF-TEN-001..008 UI) | T50-06 | `PlatformTenantsComponent`, `PlatformTenantsService`, `roleGuard(['PLATFORM_ADMIN'])` | `platform-tenants.service.spec`, `platform-tenants.component.spec`, `auth.guard.spec` |

## Observabilidad (RNF-019, RNF-020, RO-001..003, RS-014)

| Requisito | Tarea | Caso de uso / componente | Endpoint | Pruebas |
|---|---|---|---|---|
| RNF-019 Logs estructurados | T140-01 | `logging.structured.format.console=ecs` en `config/observability.yml` | — | `StructuredLoggingIntegrationTest` |
| RNF-020 Correlation ID por petición | T140-02 | `CorrelationIdFilter`, `ObservabilityContext` | cabecera `X-Correlation-Id` en toda respuesta | `StructuredLoggingIntegrationTest`, `ObservabilityContextTest` |
| RO-001 Health checks | T140-03 | `ping`/`db` (Actuator), `OutboxHealthIndicator`, `MailHealthIndicator` | `GET /actuator/health`, `/actuator/health/operations` | `HealthEndpointIntegrationTest`, `OutboxHealthIndicatorTest`, `MailHealthIndicatorTest` |
| RO-002 Campos del log (timestamp, nivel, correlación, tenant, usuario, caso de uso, resultado) | T140-01 | `RequestObservabilityInterceptor`, `ScheduledJobRunner` | — | `StructuredLoggingIntegrationTest`, `RequestObservabilityInterceptorTest` |
| RO-003 Métricas de peticiones, errores y latencia | T140-04 | `http.server.requests` (Actuator) + configuración de percentiles | `GET /actuator/metrics` | `HealthEndpointIntegrationTest` (contexto), `ApplicationSmokeTest` |
| RO-003 Métricas de Outbox | T140-04 | `OutboxMetrics` (incluye gauge `outbox.messages.dead`) | `GET /actuator/metrics` | `OutboxGuaranteesIntegrationTest` |
| RO-003 Métricas de notificaciones | T140-04 | `NotificationMetrics` | `GET /actuator/metrics` | `NotificationMetricsTest`, `SmtpEmailSenderTest` |
| RO-003 Métricas de jobs | T140-04 | `ScheduledJobRunner` (`jobs.executions`, `jobs.duration`) | `GET /actuator/metrics` | `ScheduledJobRunnerTest`, `OutboxJobsCorrelationTest` |
| RS-014 Sin contraseñas, tokens ni cookies en logs | T140-01 | esquema de log cerrado en `ObservabilityContext` | — | `NoSecretsInLogsTest`, `StructuredLoggingIntegrationTest`, `ObservabilityContextTest` |

## Documentación

| Artefacto | Tarea | Ubicación |
|---|---|---|
| ADR ciclo de vida + plataforma | — | `docs/adr/ADR-0010-...md` |
| ADR logs estructurados y observabilidad | T140 | `docs/adr/ADR-0015-logs-estructurados-y-observabilidad.md` |
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
