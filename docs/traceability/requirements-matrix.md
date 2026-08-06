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

## Seguridad de autenticación (RS-007, RS-008, RF-USR-008)

| Requisito | Tarea | Caso de uso / componente | Endpoint | Prueba |
|---|---|---|---|---|
| RF-USR-007 Gestión de sesiones | T60-01/T60-02/T60-04 | `Session`, `ListSessionsUseCase`, `RevokeSessionUseCase`, `RevokeAllSessionsUseCase`, `SessionController` | `GET /api/v1/auth/sessions`, `DELETE /api/v1/auth/sessions/{id}`, `DELETE /api/v1/auth/sessions` | `SessionTest`, `SessionControllerIntegrationTest`, `AuthControllerIntegrationTest` |
| RF-USR-006 Recuperacion de contrasena | T60-05/T60-06 | `PasswordResetToken`, `RequestPasswordResetUseCase`, `ResetPasswordUseCase`, `PasswordResetController`, `PasswordResetEmailListener` | `POST /api/v1/auth/password/forgot`, `POST /api/v1/auth/password/reset` | `PasswordResetTokenTest`, `RequestPasswordResetUseCaseTest`, `ResetPasswordUseCaseTest`, `PasswordResetControllerIntegrationTest`, `PasswordResetEmailListenerTest` |
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
## Calendarios laborales (RF-CAL)

| Requisito | Tarea | Caso de uso / componente | Endpoint | Prueba |
|---|---|---|---|---|
| RF-CAL-001 Crear calendario | T70-01/04 | `WorkCalendar.create`, `CreateWorkCalendarUseCase` | `POST /api/v1/admin/calendars` | `WorkCalendarTest`, `WorkCalendarUseCasesTest`, `AdminCalendarControllerIntegrationTest` |
| RF-CAL-002 Definir días laborables | T70-01 | `CalendarDayRule`, `WorkCalendar.isWorkingDay` | `POST`/`PUT /api/v1/admin/calendars` | `WorkCalendarTest`, `CalendarValueObjectsTest`, `FlywayCalendarMigrationIntegrationTest` |
| RF-CAL-003 Gestionar festivos | T70-01 | `Holiday`, `WorkCalendar.dayOf` | `PUT /api/v1/admin/calendars/{id}` | `WorkCalendarTest`, `AdminCalendarControllerIntegrationTest` |
| RF-CAL-004 Jornadas especiales | T70-01 | `SpecialDay`, `WorkCalendar.dayOf` | `PUT /api/v1/admin/calendars/{id}` | `WorkCalendarTest`, `CalendarRepositoryAdapterIntegrationTest` |
| RF-CAL-005 Vigencia temporal | T70-01 | `WorkCalendar.isEffectiveOn` | `POST`/`PUT /api/v1/admin/calendars` | `WorkCalendarTest`, `EffectiveCalendarResolverTest` |
| RF-CAL-006 Asignación por tenant/equipo/empleado | T70-02/04 | `CalendarAssignment`, `EffectiveCalendarResolver`, `AssignCalendarUseCase` | `POST`/`GET`/`DELETE /api/v1/admin/calendar-assignments` | `CalendarAssignmentTest`, `EffectiveCalendarResolverTest`, `AdminCalendarAssignmentControllerIntegrationTest` |
| RF-CAL-006 Precedencia: la más específica prevalece | T70-02 | `AssignmentScope.specificity`, `EffectiveCalendarResolver.resolve` | `GET /api/v1/admin/calendar-assignments/effective` | `EffectiveCalendarResolverTest`, `AdminCalendarAssignmentControllerIntegrationTest` |
| RF-CAL-007 Zona horaria del calendario | T70-01 | `WorkCalendar.zoneId`, `startOfDay`, `endOfDayExclusive` | `POST`/`PUT /api/v1/admin/calendars` | `WorkCalendarDaylightSavingTest` |
| RNF-011 Instantes en UTC, fechas locales como `DATE` | T70-03 | `V16__calendar.sql`, `WorkCalendarJpaEntity` | — | `FlywayCalendarMigrationIntegrationTest`, `CalendarRepositoryAdapterIntegrationTest` |
| RNF-012 Cambio de horario de verano (23 h / 25 h) | T70-01 | `WorkCalendar.civilDayLength` | — | `WorkCalendarDaylightSavingTest` |
| RT-003 Cross-tenant en calendarios y asignaciones | T70-04/05 | repositorios tenant-scoped, 404 en vez de 403 | todos los de calendario | `AdminCalendarControllerIntegrationTest`, `AdminCalendarAssignmentControllerIntegrationTest`, `CalendarRepositoryAdapterIntegrationTest` |
| RT-004 Solo `TENANT_ADMIN` administra calendarios | T70-05 | `@PreAuthorize("hasRole('TENANT_ADMIN')")` | todos los de calendario | `AdminCalendarControllerIntegrationTest`, `AdminCalendarAssignmentControllerIntegrationTest` |

## Frontend de calendarios

| Requisito | Tarea | Componente | Prueba |
|---|---|---|---|
| UI de calendarios (RF-CAL-001..006) | T70-05 | `AdminCalendarsComponent`, `CalendarsService`, `roleGuard(['TENANT_ADMIN'])` | `admin-calendars.component.spec`, `calendars.service.spec` |

## Reglas horarias y evaluación de jornada (RF-TIM-011..014)

| Requisito | Tarea | Caso de uso / componente | Endpoint | Prueba |
|---|---|---|---|---|
| RF-TIM-011 Horas extra | T72-01/T72-02 | `HourlyRules`, `WorkdayEvaluationEngine`, `EvaluateClosedWorkdayService` | — | `WorkdayEvaluationEngineTest` |
| RF-TIM-013 Límites de jornada | T72-01/T72-02 | `HourlyRules.maxDailyWork`, `WorkdayAnomaly.MAX_DAILY_WORK_EXCEEDED` | — | `WorkdayEvaluationEngineTest` |
| RF-TIM-014 Descansos obligatorios | T72-01/T72-02 | `HourlyRules.requiredBreak`, `WorkdayAnomaly.REQUIRED_BREAK_NOT_MET` | — | `WorkdayEvaluationEngineTest` |
| Configuración admin de reglas horarias | T72-01 | `GetHourlyRulesUseCase`, `UpdateHourlyRulesUseCase`, `AdminHourlyRulesController` | `GET/PUT /api/v1/admin/hourly-rules` | `HourlyRulesUseCasesTest`, `AdminHourlyRulesControllerIntegrationTest` |
| Evaluación al cerrar/ajustar jornada | T72-03 | `EndWorkdayUseCase`, `ApproveCorrectionRequestUseCase`, `WorkdayEvaluationRepository` | — | `EndWorkdayUseCaseTest`, `ApproveCorrectionRequestUseCaseTest`, `FlywayWorkdayEvaluationMigrationIntegrationTest` |
| Reporting con evaluación horaria | T100-01/T100-03/T100-04 | `WorkdaySummaryQueryPortAdapter`, `TimeSummaryCalculator`, `ReportRestMapper`, `TimeSummaryCsvWriter` | `GET /api/v1/reports/**`, `GET /api/v1/reports/tenant/export.csv` | `TimeSummaryCalculatorTest`, `TimeSummaryCsvWriterTest`, `ReportControllerIntegrationTest` |

## Ausencias (RF-ABS)

| Requisito | Tarea | Caso de uso / componente | Endpoint | Prueba |
|---|---|---|---|---|
| RF-ABS-001 Tipos de ausencia | T80-01/T80-05 | `AbsenceType`, `ListAbsenceTypesUseCase`, `AppAbsenceController` | `GET /api/v1/app/absence-types` | `AbsenceTypeTest`, `AppAbsenceControllerIntegrationTest` |
| RF-ABS-002 Solicitud de ausencia | T80-02/T80-04/T80-05 | `AbsenceRequest`, `RequestAbsenceUseCase`, `AppAbsenceController` | `POST /api/v1/app/absences` | `AbsenceRequestTest`, `RequestAbsenceUseCaseTest`, `AppAbsenceControllerIntegrationTest` |
| RF-ABS-003 Aprobación o rechazo | T80-04/T80-05 | `ApproveAbsenceRequestUseCase`, `RejectAbsenceRequestUseCase`, `AdminAbsenceController` | `POST /api/v1/admin/absences/{id}/approve`, `POST /api/v1/admin/absences/{id}/reject` | `ResolveAbsenceRequestUseCasesTest`, `AdminAbsenceControllerIntegrationTest` |
| RF-ABS-005 Historial | T80-04/T80-05 | `ListOwnAbsenceRequestsUseCase`, `ListTenantAbsenceRequestsUseCase` | `GET /api/v1/app/absences`, `GET /api/v1/admin/absences` | `AbsenceRequestRepositoryAdapterIntegrationTest`, `AppAbsenceControllerIntegrationTest`, `AdminAbsenceControllerIntegrationTest` |

## Turnos (RF-SHF)

| Requisito | Tarea | Caso de uso / componente | Endpoint | Prueba |
|---|---|---|---|---|
| RF-SHF-001 Plantillas de turno | T90-01/T90-04/T90-05 | `ShiftTemplate`, `CreateShiftTemplateUseCase`, `AdminShiftController` | `GET/POST /api/v1/admin/shifts/templates` | `ShiftTemplateTest`, `ShiftTemplateUseCasesTest`, `AdminShiftControllerIntegrationTest` |
| RF-SHF-002 Asignación de turno | T90-02/T90-04/T90-05 | `ShiftAssignment`, `AssignShiftUseCase`, `AdminShiftController` | `POST /api/v1/admin/shifts/assignments` | `ShiftAssignmentTest`, `ShiftAssignmentUseCasesTest`, `AdminShiftControllerIntegrationTest` |
| RF-SHF-003 Turnos nocturnos | T90-01/T90-05 | `ShiftTemplate.crossesMidnight`, `AppShiftController` | `GET /api/v1/app/shifts` | `ShiftTemplateTest`, `AppShiftControllerIntegrationTest` |
| RF-SHF-005 Comparación previsto-real | T90-06 | `ResolvePlannedShiftUseCase`, `WorkdayEvaluationEngine`, `EvaluateClosedWorkdayService` | — (se refleja en la evaluación de la jornada) | `ResolvePlannedShiftUseCaseTest`, `EvaluateClosedWorkdayServiceTest` |
| RF-SHF-006 Consulta de turnos | T90-04/T90-05 | `ListOwnEffectiveShiftsUseCase`, `AppShiftController` | `GET /api/v1/app/shifts` | `AppShiftControllerIntegrationTest` |

## Documentación

| Artefacto | Tarea | Ubicación |
|---|---|---|
| ADR ciclo de vida + plataforma | — | `docs/adr/ADR-0010-...md` |
| ADR logs estructurados y observabilidad | T140 | `docs/adr/ADR-0015-logs-estructurados-y-observabilidad.md` |
| ADR solicitud de alta separada del tenant | T53 | `docs/adr/ADR-0016-solicitud-de-alta-separada-del-tenant.md` |
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
| RO-006 Política de retención | T150-01, T150-02 | Rotación *grandfather-father-son* en `backup-postgres.sh` (7 días + 4 semanas ISO) | `docs/adr/ADR-0016-estrategia-backup-retencion.md`; rotación probada con 9 backups sintéticos: elimina los que caen fuera de ambas ventanas y conserva el primero de cada semana ISO |
| RO-007 Procedimiento de restauración documentado y probado | T150-03, T150-04 | `scripts/backup/restore-postgres.sh` (parar → restaurar → validar → arrancar) + `scripts/smoke.sh` | `docs/manuals/backup-restore.md` §3 y §4; simulacro ejecutado 2026-08-04 |
| RT-008 Restauración validada y documentada | T150-04 | Validación automática en el script (esquema, Flyway sin fallos, tablas de negocio con filas) y smoke tests | Simulacro 2026-08-04: 13 s, `TRUNCATE tenant CASCADE` recuperado a 3 tenants / 5 usuarios / 8 outbox, login HTTP 200, `scripts/smoke.sh` en verde. Acta completa en `docs/manuals/backup-restore.md` §4 |
| RC-008 Sin alta disponibilidad (restricción asumida) | T150-01 | Backup lógico diario en lugar de PITR/réplica; RPO 24 h, RTO 1 h declarados | `docs/adr/ADR-0016-estrategia-backup-retencion.md` (alternativas descartadas) |

## Pendiente (fuera de esta iteración)

- T130-04/05 Auditoría de tenant ampliada y consulta avanzada.
- RF-TEN-001: `número de usuarios` y `último acceso` en el listado (dependen de
  identity/sesiones — épica T60).

## Notificaciones (RF-NOT)

| Requisito | Tarea | Caso de uso / componente | Endpoint | Pruebas |
|---|---|---|---|---|
| RF-NOT-001 Notificaciones internas | T110-06 | `ListOwnNotificationsUseCase`, `NotificationController`, `NotificationsComponent` | `GET /api/v1/notifications`, `GET /api/v1/notifications/unread-count` | `NotificationControllerIntegrationTest`, `notifications.component.spec` |
| RF-NOT-002 Notificaciones por correo | T110-05 | `NotificationSender`, `NotificationEmailComposer`, puerto `EmailSender` | — (tarea programada) | `NotificationSenderTest` |
| RF-NOT-003 Eventos notificables | T110-04 | `NotificationEventListener`, `AbsenceIntegrationEventMapper` | — | `NotificationEventListenerTest` |
| RF-NOT-004 Procesamiento asíncrono | T110-04, T110-05 | Outbox → `NotificationEventListener` → `NotificationDeliveryJob` | — | `NotificationEventListenerTest`, `NotificationSenderTest` |
| RF-NOT-005 Reintentos | T110-05 | `Notification.markAttemptFailed`, `NotificationDeliveryProperties.maxAttempts` | — | `NotificationTest`, `NotificationSenderTest` |
| RF-NOT-006 Trazabilidad del envío | T110-02 | `Notification` (estado, intentos, último error, fechas), migración V24 | — | `NotificationTest` |
| RF-OUT-005 Consumidores idempotentes | T110-04 | `ProcessedEventStore` con clave `(eventId, consumidor)` | — | `NotificationEventListenerTest`, `JdbcProcessedEventStoreIntegrationTest` |
