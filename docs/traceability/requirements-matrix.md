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

## Registro público de tenants (RF-REG) — épica T53

| Requisito | Tarea | Caso de uso / componente | Endpoint | Pruebas |
|---|---|---|---|---|
| RF-REG-001 Solicitud de alta | T53-01, T53-03 | `TenantRegistration`, `RequestTenantRegistrationUseCase`, `PublicTenantRegistrationController` | `POST /api/v1/public/tenant-registrations` | `TenantRegistrationTest`, `RequestTenantRegistrationUseCaseTest`, `PublicTenantRegistrationControllerIntegrationTest` |
| RF-REG-002 Validación de datos (incl. términos) | T53-01 | `TenantRegistrationRequestBody` (Bean Validation), invariantes de `TenantRegistration` | `POST /api/v1/public/tenant-registrations` | `TenantRegistrationTest`, `PublicTenantRegistrationControllerIntegrationTest` |
| RF-REG-003 Protección contra abuso (IP y correo) | T53-03 | `RegistrationProperties.Throttle`, `IpHasher`/`Sha256IpHasher`, `RequestTenantRegistrationUseCase` | `POST /api/v1/public/tenant-registrations` | `Sha256IpHasherTest`, `RequestTenantRegistrationUseCaseTest`, `PublicTenantRegistrationControllerIntegrationTest` |
| RF-REG-004 Verificación de correo | T53-05 | `TenantRegistration.verifyEmail`, `VerifyTenantRegistrationEmailUseCase`, `TenantRegistrationEmailListener` (Outbox, ADR-0012) | `POST /api/v1/public/tenant-registrations/verify-email` | `TenantRegistrationTest`, `VerifyTenantRegistrationEmailUseCaseTest`, `TenantRegistrationEmailListenerTest`, `PublicTenantRegistrationControllerIntegrationTest` |
| RF-REG-004 Reenvío limitado | T53-05 | `TenantRegistration.resendVerification`, `ResendTenantRegistrationVerificationUseCase` | `POST /api/v1/public/tenant-registrations/resend-verification` | `TenantRegistrationTest`, `ResendTenantRegistrationVerificationUseCaseTest` |
| RF-REG-005 Mensajes anti-enumeración | T53-03, T53-05 | `TenantRegistrationAcceptedResponse`, casos de uso sin excepción de negocio | los tres endpoints públicos | `RequestTenantRegistrationUseCaseTest`, `ResendTenantRegistrationVerificationUseCaseTest`, `PublicTenantRegistrationControllerIntegrationTest` |
| RF-REG-006 Auditoría del registro | T53-03 | `RegistrationAuditTrail`/`AnonymousRegistrationAuditTrail`, `AuditRecorder` en aprobación y rechazo | — | `RequestTenantRegistrationUseCaseTest`, `ApproveTenantRegistrationUseCaseTest`, `PlatformTenantRegistrationControllerIntegrationTest` |
| RF-TEN-003 Creación por registro público controlado | T53-03 | `ApproveTenantRegistrationUseCase` (tenant en `PENDING` vía `Tenant.requestRegistration`) | `POST /api/v1/platform/registrations/{id}/approve` | `ApproveTenantRegistrationUseCaseTest`, `PlatformTenantRegistrationControllerIntegrationTest` |
| RF-TEN-010 Registro público deshabilitable (flujo V2) | T53-04 | `PublicTenantRegistrationController` + flag `registration.public.enabled` (`config/registration.yml`) | los tres endpoints públicos (403 si off) | `PublicRegistrationDisabledIntegrationTest` |
| Revisión y rechazo de solicitudes | T53-03 | `ListTenantRegistrationsUseCase`, `RejectTenantRegistrationUseCase`, `PlatformTenantRegistrationController` | `GET /api/v1/platform/registrations`, `POST .../{id}/reject` | `ListTenantRegistrationsUseCaseTest`, `RejectTenantRegistrationUseCaseTest`, `PlatformTenantRegistrationControllerIntegrationTest` |
| Aislamiento por rol de las solicitudes (RT-003, RT-004) | T53-03 | `@PreAuthorize('hasRole(PLATFORM_ADMIN)')` en `PlatformTenantRegistrationController` | `/api/v1/platform/registrations/**` | `PlatformTenantRegistrationControllerIntegrationTest` (TENANT_ADMIN/EMPLOYEE → 403, anónimo → 401) |

## Frontend del registro público

| Requisito | Tarea | Componente | Prueba |
|---|---|---|---|
| RF-REG-001/002 pantalla pública de alta | T53-03 | `TenantRegistrationComponent`, `RegistrationService`, ruta `/registro` | `tenant-registration.component.spec`, `registration.service.spec` |
| RF-REG-004 pantalla de verificación y reenvío | T53-05 | `VerifyRegistrationEmailComponent`, ruta `/registro/verificar` | `verify-registration-email.component.spec` |
| Bandeja de solicitudes en plataforma | T53-03 | `PlatformRegistrationsComponent`, `PlatformRegistrationsService`, ruta `/platform/registrations` | `platform-registrations.component.spec`, `platform-registrations.service.spec` |

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
| ADR solicitud de alta separada del tenant | T53 | `docs/adr/ADR-0013-solicitud-de-alta-separada-del-tenant.md` |
| Catálogo de eventos `tenant.*.v1` | — | `docs/integration/event-catalog.md` |
| OpenAPI `/api/v1/platform/**` | — | `docs/api/openapi.yaml` |
| Reglas del agente V2 | T00-03 | `AGENTS.md` |

## Pendiente (fuera de esta iteración)

- T130-04/05 Auditoría de tenant ampliada y consulta avanzada.
- RF-TEN-001: `número de usuarios` y `último acceso` en el listado (dependen de
  identity/sesiones — épica T60).
