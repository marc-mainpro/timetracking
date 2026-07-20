# Estado de tareas

| Tarea | Título | Estado | Notas |
|---|---|---|---|
| T101 | Scaffolding backend | hecha | validada: mvn verify verde, Spring Boot 3.5.9 |
| T102 | Scaffolding frontend | hecha | validada: build+lint+9 tests verdes, Angular 19.2 |
| T103 | Docker Compose + Flyway | hecha | validada: compose up → health UP, Flyway v1 aplicada |
| T104 | CI | hecha | validada: YAML ok, comandos = validados localmente; pendiente 1ª ejecución real al crear remote |
| T105 | Docs, ADR, AGENTS, skills | hecha | validada: AGENTS literal, 8 skills completas, ADR 1-6 |
| T106 | ArchUnit | hecha | validada: 14 tests verdes, detección de violaciones probada |
| T201 | Migración identidad | hecha | validada originalmente con identidad base; la unicidad final de `app_user.email` pasó a global en `T204` mediante `V3` |
| T202 | Dominio Tenant/User | hecha | validada: 53 tests, dominio ~100 % cobertura; detectó gap de tests *IT |
| T203 | RegisterTenant | hecha | validada: `mvn -B verify` verde (65 tests), cobertura JaCoCo en verde, commit `1c909ed` |
| T204 | Autenticación JWT | hecha | validada: `mvn -B verify` verde (84 tests), JWT+refresh rotatorio+logout, ADR-0008 email global único |
| T205 | Rate limiting + tests seguridad | hecha | validada: `mvn -B verify` verde (91 tests), Bucket4j en login/register, 429 y cabeceras de auth cubiertas |
| T301 | TenantContext | hecha | validada: `mvn -B verify` verde (99 tests), tenant/user/roles desde JWT, correlationId por request y revocación por inactividad en cada request autenticada |
| T302 | Repos tenant-aware | hecha | validada: `mvn -B verify` verde (105 tests), `UserRepository.findById(tenantId,id)` y convención verificada por test |
| T303 | Suite cross-tenant | hecha | validada: `mvn -B verify` verde (105 tests), fixture reusable + 404/403/401 cross-tenant; caso de mismo email sustituido por ADR-0008 |
| T401 | Dominio Workday | hecha | validada: `mvn -B verify` verde (124 tests), agregado Workday + BreakEntry + eventos de dominio |
| T402 | Persistencia Workday | hecha | validada: `mvn -B verify` verde (131 tests), V4 aplicada, repositorio JPA tenant-aware y optimistic locking |
| T403 | API fichaje | hecha | validada: `mvn -B verify` verde (142 tests), flujo completo de fichaje + admin workdays + suite cross-tenant ampliada |
| T404 | Frontend empleado | hecha | validada: `npm test -- --watch=false --browsers=ChromeHeadless` y `npm run build` verdes; auth real + dashboard + historial propio |
| T501 | API gestión empleados | hecha | validada: `mvn -B verify` verde (152 tests), CRUD admin de empleados, regla LAST_ADMIN y revocación de refresh |
| T502 | Frontend admin empleados | hecha | validada: `npm test -- --watch=false --browsers=ChromeHeadless` y `npm run build` verdes; listado admin, alta/edición y manejo de 409 |
| T601 | Dominio correcciones | hecha | validada: `mvn -B verify` verde (164 tests), agregado CorrectionRequest + JSONB + ajuste de Workday |
| T602 | API correcciones | hecha | validada: `mvn -B verify` verde (173 tests), request/approve/reject/list/get de correcciones + suite cross-tenant ampliada |
| T603 | Auditoría | hecha | validada: `mvn -B verify` verde (175 tests), auditoría append-only tenant-aware + `GET /api/v1/admin/audit-events` |
| T604 | Concurrencia | hecha | validada: `mvn -B verify` verde (179 tests), carreras estables sin sleeps + `CorrectionRequest.version` + estrategia documentada |
| T605 | Frontend correcciones | hecha | validada: `npm test -- --watch=false --browsers=ChromeHeadless` y `npm run build` verdes; flujo empleado/admin implementado, verificación manual local pendiente |
| T701 | Migración outbox | hecha | validada: `mvn -B verify` verde (190 tests), `V8__outbox.sql` (número real tras V7), `OutboxMessageRepository`/`OutboxWriter` + reclamación `FOR UPDATE SKIP LOCKED` con test de concurrencia real (dos workers, sin sleeps) |
| T702 | Eventos integración + atomicidad | hecha | validada: `mvn -B verify` verde (209 tests), `IntegrationEvent` (`shared.domain`) + mapper por módulo + `OutboxDomainEventPublisher` sustituye a `LoggingDomainEventPublisher` (eliminada), tests de atomicidad commit⇒PENDING y rollback⇒0 filas |
| T703 | Publicador outbox | hecha | validada: `mvn -B verify` verde (266 tests), `OutboxPublisherJob`/`OutboxArchiverJob` (`@Scheduled`, `outbox.*` properties) + `PublishPendingOutboxMessages`/`RetryFailedOutboxMessage`/`ArchivePublishedOutboxMessages` + backoff exponencial con jitter + métricas Micrometer + recuperación de `PROCESSING` huérfanos + tests sin sleeps (reloj mutable inyectado) incl. concurrencia de dos publicadores |
| T704 | Idempotencia + catálogo | pendiente | |
| T801 | API informes + CSV | hecha | validada: `mvn -B verify` verde (219 tests), resumen diario/tenant con zona horaria del tenant + tests DST (23h/25h Madrid) + export CSV + suite cross-tenant ampliada |
| T802 | Frontend informes | hecha | validada: `npm test -- --watch=false --browsers=ChromeHeadless` (34 tests) y `npm run build` verdes; `/admin/reports` (rango+tabla+CSV) y `/reports` propio de empleado |
| T901 | E2E | pendiente | |
| T902 | Hardening OWASP | pendiente | |
| T903 | Cobertura + docs finales | pendiente | |
| T1001 | Dockerización final | pendiente | |
| T1002 | Manuales + demo | pendiente | |
