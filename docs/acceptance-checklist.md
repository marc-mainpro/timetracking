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
