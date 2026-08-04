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
| ADR logs estructurados y observabilidad | T140 | `docs/adr/ADR-0013-logs-estructurados-y-observabilidad.md` |
| Catálogo de eventos `tenant.*.v1` | — | `docs/integration/event-catalog.md` |
| OpenAPI `/api/v1/platform/**` | — | `docs/api/openapi.yaml` |
| Reglas del agente V2 | T00-03 | `AGENTS.md` |

## Pendiente (fuera de esta iteración)

- T53 Registro público controlado (TenantRegistration, verificación de correo,
  feature flag).
- T130-04/05 Auditoría de tenant ampliada y consulta avanzada.
- RF-TEN-001: `número de usuarios` y `último acceso` en el listado (dependen de
  identity/sesiones — épica T60).
