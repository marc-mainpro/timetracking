# Coverage Report

## Backend

Validación ejecutada con `cd backend && mvn -B verify`.

| Capa | Umbral | Evidencia |
|---|---|---|
| `*.domain*` | >= 90 % | Gate JaCoCo en `backend/pom.xml` y reporte HTML `backend/target/site/jacoco/index.html` |
| `*.application*` | >= 80 % | Gate JaCoCo en `backend/pom.xml` y reporte HTML `backend/target/site/jacoco/index.html` |
| REST/seguridad/outbox | sin umbral separado | Cobertura reforzada con `EndToEndFlowIT`, pruebas de seguridad, correcciones y reportes |

El build publica el HTML JaCoCo como artefacto `jacoco-report` en CI.

## Frontend

Validación ejecutada con `cd frontend && npm run test:coverage`.

| Área | Evidencia |
|---|---|
| Guards / interceptores core | `auth.guard.spec.ts`, `auth.interceptor.spec.ts` |
| Servicios core | `auth.service.spec.ts`, `error-messages.service.spec.ts` |
| Servicios feature | `workdays.service.spec.ts`, `admin-employees.service.spec.ts`, `corrections.service.spec.ts`, `reports.service.spec.ts` |
| Componentes principales | suites existentes de login, register, dashboard, workdays, employees, corrections y reports |

El build publica `frontend/coverage/` como artefacto `frontend-coverage`.

## Cierre

- Backend: cobertura verificada por `mvn verify` con gates activos.
- Frontend: cobertura generada en CI y ampliada en servicios/guards/interceptor
  que antes carecían de tests directos.
