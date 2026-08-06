# Acceptance Checklist

## Producto

- SaaS multitenant con aislamiento por tenant: cubierto por
  `CrossTenantSecurityIntegrationTest` y `EndToEndFlowIT`.
- Registro de organización y primer admin: `POST /api/v1/public/tenant-registrations` + aprobación desde plataforma,
  `AuthRegisterControllerIntegrationTest`.
- Gestión de empleados por admin: `EmployeeControllerIntegrationTest`,
  frontend `admin-employees`.
- Fichaje completo con pausa: `WorkdayControllerIntegrationTest`,
  `EndToEndFlowIT`, frontend `workdays`.
- Correcciones solicitadas y resueltas: `CorrectionControllerIntegrationTest`,
  `AuditEventControllerIntegrationTest`, frontend `corrections`.
- Informes y CSV: `ReportControllerIntegrationTest`, frontend `reports`.
- Calendarios laborales con festivos, jornadas especiales y vigencia, asignables
  a organización, equipo o empleado con la regla «la asignación más específica
  prevalece»: `WorkCalendarTest`, `EffectiveCalendarResolverTest`,
  `AdminCalendarControllerIntegrationTest`,
  `AdminCalendarAssignmentControllerIntegrationTest`, frontend `calendars`.

## Seguridad

- JWT + refresh rotatorio + logout: `AuthControllerIntegrationTest`.
- Gestión de sesiones del usuario autenticado: `SessionControllerIntegrationTest`.
- Rate limiting y hardening OWASP: `AuthSecurityIntegrationTest`,
  `docs/security/owasp-review.md`.
- Ningún endpoint privado sin auth: `RouteAuthorizationIntegrationTest`.
- Problem Details sin fuga interna: `GlobalExceptionHandlerIntegrationTest`.
- Detección de secretos en CI sobre toda la historia de git (RS-016): job
  `secret-scan` de `.github/workflows/ci.yml`, `.gitleaks.toml`; 124 commits
  escaneados sin hallazgos y prueba negativa verificada.
- Análisis de dependencias vulnerables en CI (RS-015): jobs
  `frontend-dependencies` y `backend-dependencies`,
  `scripts/security/npm-audit-gate.sh`. Política de severidad, excepciones y
  aprobaciones en `docs/security/dependency-scanning-policy.md`. Parcial en
  backend hasta dar de alta el secreto `NVD_API_KEY`.
- Rate limiting por endpoint y patrón, con 429 y `RATE_LIMIT_EXCEEDED`
  (RS-007, T30-03): `RateLimitFilterIntegrationTest`, `RateLimitPropertiesTest`.
- Bloqueo temporal de cuenta con umbral y duración configurables, reinicio tras
  login correcto y auditoría (RF-USR-008, RS-008, T30-04): `AccountLockoutTest`,
  `AccountLockoutServiceTest`, `AccountLockoutIntegrationTest`.
- Una cuenta bloqueada no se distingue de una inexistente ante credenciales
  incorrectas (anti-enumeración, RS-008):
  `AccountLockoutIntegrationTest#lockedAccountIsIndistinguishableFromAnUnknownAccount`.

## Registro público controlado (T53)

- El alta pública crea una solicitud, nunca un tenant activo:
  `PublicTenantRegistrationControllerIntegrationTest`, `TenantRegistrationTest`.
- El tenant nace en `PENDING` al aprobar la solicitud, y la aprobación es
  idempotente: `ApproveTenantRegistrationUseCaseTest`,
  `PlatformTenantRegistrationControllerIntegrationTest`.
- Verificación de correo con token de un solo uso, caducidad y reenvío limitado:
  `TenantRegistrationTest`, `VerifyTenantRegistrationEmailUseCaseTest`,
  `ResendTenantRegistrationVerificationUseCaseTest`,
  `PublicTenantRegistrationControllerIntegrationTest`.
- Del token solo se almacena el hash y el correo sale por el Outbox:
  `Sha256VerificationTokenGeneratorTest`, `TenantRegistrationEmailListenerTest`.
- Respuestas indistinguibles para un correo existente y uno inexistente
  (RF-REG-005): `PublicTenantRegistrationControllerIntegrationTest`,
  `RequestTenantRegistrationUseCaseTest`.
- Solo `PLATFORM_ADMIN` ve y decide sobre las solicitudes:
  `PlatformTenantRegistrationControllerIntegrationTest`.
- Flag `registration.public.enabled` apagado ⇒ 403 en los tres endpoints
  públicos: `PublicRegistrationDisabledIntegrationTest`.
- Frontend: alta pública, verificación y bandeja de plataforma:
  `tenant-registration.component.spec`, `verify-registration-email.component.spec`,
  `platform-registrations.component.spec`.

## Multitenancy

- `tenantId` nunca se confía al cliente: documentado en `AGENTS.md`, validado
  por repos tenant-aware y suites cross-tenant.
- Dos tenants operan sin compartir datos: `EndToEndFlowIT`.
- Un tenant no puede leer ni modificar calendarios ni asignaciones de otro, y
  recibe 404 en vez de 403 para no filtrar su existencia:
  `AdminCalendarControllerIntegrationTest`,
  `AdminCalendarAssignmentControllerIntegrationTest`,
  `CalendarRepositoryAdapterIntegrationTest`.

## Outbox y eventos

- Persistencia atómica negocio + outbox: pruebas T702/T704.
- Publicación con reintentos e idempotencia: `OutboxGuaranteesIntegrationTest`
  y documentación en `docs/integration/`.

## Testing y cobertura

- Backend `mvn -B verify`: requerido en CI y documentado en
  `docs/testing/coverage-report.md`.
- Frontend `npm run test:coverage`: requerido en CI y documentado en
  `docs/testing/coverage-report.md`.
- E2E API-level del flujo MVP: `EndToEndFlowIT`.

## Operación y demo

- `docker compose up` deja backend + frontend utilizables: `docker-compose.yml`,
  `frontend/Dockerfile`, `frontend/nginx.conf`.
- Smoke reproducible: `scripts/smoke.sh`.
- Datos base para demo: `scripts/seed-demo.sh`.
- Manuales y guion: `docs/manuals/*.md`, `docs/demo/demo-script.md`.
- Backup automatizado con retención y cifrado (RO-005, RO-006):
  `scripts/backup/backup-postgres.sh`, estrategia en
  `docs/adr/ADR-0016-estrategia-backup-retencion.md`.
- Restauración documentada y **probada de verdad** (RO-007, RT-008):
  `scripts/backup/restore-postgres.sh`; acta del simulacro del 2026-08-04
  (13 s, smoke tests en verde, incidencia detectada y corregida) en
  `docs/manuals/backup-restore.md` §4.
- Logs estructurados con correlación, tenant, usuario, caso de uso y resultado
  (RNF-019, RO-002): `StructuredLoggingIntegrationTest`.
- Correlation ID también en las tareas programadas (RNF-020):
  `OutboxJobsCorrelationTest`, `ScheduledJobRunnerTest`.
- Health checks de aplicación, PostgreSQL, Outbox y correo, con detalle solo
  para administradores autenticados (RO-001): `HealthEndpointIntegrationTest`,
  `OutboxHealthIndicatorTest`, `MailHealthIndicatorTest`.
- Métricas de peticiones, errores, latencia, Outbox, notificaciones y jobs
  (RO-003): `/actuator/metrics`, `NotificationMetricsTest`,
  `ScheduledJobRunnerTest`.
- Sin contraseñas, tokens ni cookies en los logs (RS-014):
  `NoSecretsInLogsTest`, `StructuredLoggingIntegrationTest`.
- Notificaciones internas con contador de no leídas y marcado de lectura
  (RF-NOT-001, T110-06/07): `NotificationControllerIntegrationTest`,
  `frontend/src/app/features/notifications/`.
- Notificaciones generadas desde eventos de integración e idempotentes ante
  reentrega (RF-NOT-003, RF-OUT-005, T110-04): `NotificationEventListenerTest`.
- Envío por correo con reintentos y estado final `FAILED` sin perder el aviso en
  la aplicación (RF-NOT-002, RF-NOT-005, RF-NOT-006, T110-05):
  `NotificationSenderTest`, `NotificationTest`.
