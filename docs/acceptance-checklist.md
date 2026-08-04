# Acceptance Checklist

## Producto

- SaaS multitenant con aislamiento por tenant: cubierto por
  `CrossTenantSecurityIntegrationTest` y `EndToEndFlowIT`.
- Registro de organización y primer admin: `POST /api/v1/auth/register`,
  `AuthRegisterControllerIntegrationTest`.
- Gestión de empleados por admin: `EmployeeControllerIntegrationTest`,
  frontend `admin-employees`.
- Fichaje completo con pausa: `WorkdayControllerIntegrationTest`,
  `EndToEndFlowIT`, frontend `workdays`.
- Correcciones solicitadas y resueltas: `CorrectionControllerIntegrationTest`,
  `AuditEventControllerIntegrationTest`, frontend `corrections`.
- Informes y CSV: `ReportControllerIntegrationTest`, frontend `reports`.

## Seguridad

- JWT + refresh rotatorio + logout: `AuthControllerIntegrationTest`.
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

## Multitenancy

- `tenantId` nunca se confía al cliente: documentado en `AGENTS.md`, validado
  por repos tenant-aware y suites cross-tenant.
- Dos tenants operan sin compartir datos: `EndToEndFlowIT`.

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
  `docs/adr/ADR-0013-estrategia-backup-retencion.md`.
- Restauración documentada y **probada de verdad** (RO-007, RT-008):
  `scripts/backup/restore-postgres.sh`; acta del simulacro del 2026-08-04
  (13 s, smoke tests en verde, incidencia detectada y corregida) en
  `docs/manuals/backup-restore.md` §4.
