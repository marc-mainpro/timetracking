# Estrategia de testing

Fuente de verdad: `tasks/_context/CONTEXT-GLOBAL.md` §8.

## Niveles

- **Unitario**: reglas de dominio y casos de uso, sin infraestructura.
  Cobertura mínima: dominio ≥ 90 %, aplicación ≥ 80 % (verificado por
  JaCoCo en el build).
- **Integración**: Testcontainers con PostgreSQL real. Cubre repositorios,
  migraciones Flyway, seguridad (autenticación/autorización), multitenancy
  (incluye tests de acceso cruzado entre tenants) y controladores REST.
- **E2E de API**: `EndToEndFlowIT` ejecuta el flujo MVP completo contra el
  backend real (registro, autenticación, empleados, jornada, corrección,
  auditoría y outbox). El E2E de navegador queda fuera del MVP y se documenta
  explícitamente como trabajo posterior.
- **Arquitectura**: ArchUnit, verificando las reglas de Clean Architecture
  de `tasks/_context/CONTEXT-GLOBAL.md` §4 (dependencias de `domain`,
  controladores sin lógica de negocio, sin ciclos, separación de entidades
  JPA/dominio/DTO).

## Reglas

- Toda regla de negocio (ver `docs/domain/reglas-de-negocio.md`) tiene al
  menos un test unitario.
- Prohibido bajar umbrales de cobertura u omitir tests "por tiempo".
- Los cálculos de límites de día en zona horaria del tenant incluyen tests
  de cambio horario estacional (DST).
- Los flujos de Outbox incluyen tests de atomicidad (mismo commit) y de
  reintentos sin duplicar efectos.

## Informe de cobertura

- Backend: `docs/testing/coverage-report.md` + HTML generado en
  `backend/target/site/jacoco/index.html` y publicado como artefacto en CI.
- Frontend: `npm run test:coverage` genera `frontend/coverage/`, también
  publicado como artefacto en CI.
