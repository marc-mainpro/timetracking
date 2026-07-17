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
| T205 | Rate limiting + tests seguridad | pendiente | |
| T301 | TenantContext | pendiente | |
| T302 | Repos tenant-aware | pendiente | |
| T303 | Suite cross-tenant | pendiente | |
| T401 | Dominio Workday | pendiente | |
| T402 | Persistencia Workday | pendiente | |
| T403 | API fichaje | pendiente | |
| T404 | Frontend empleado | pendiente | |
| T501 | API gestión empleados | pendiente | |
| T502 | Frontend admin empleados | pendiente | |
| T601 | Dominio correcciones | pendiente | |
| T602 | API correcciones | pendiente | |
| T603 | Auditoría | pendiente | |
| T604 | Concurrencia | pendiente | |
| T605 | Frontend correcciones | pendiente | |
| T701 | Migración outbox | pendiente | |
| T702 | Eventos integración + atomicidad | pendiente | |
| T703 | Publicador outbox | pendiente | |
| T704 | Idempotencia + catálogo | pendiente | |
| T801 | API informes + CSV | pendiente | |
| T802 | Frontend informes | pendiente | |
| T901 | E2E | pendiente | |
| T902 | Hardening OWASP | pendiente | |
| T903 | Cobertura + docs finales | pendiente | |
| T1001 | Dockerización final | pendiente | |
| T1002 | Manuales + demo | pendiente | |
