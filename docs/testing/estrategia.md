# Estrategia de testing

Fuente de verdad: `tasks/_context/CONTEXT-GLOBAL.md` §8.

## Niveles

- **Unitario**: reglas de dominio y casos de uso, sin infraestructura.
  Cobertura mínima: dominio ≥ 90 %, aplicación ≥ 80 % (verificado por
  JaCoCo en el build).
- **Integración**: Testcontainers con PostgreSQL real. Cubre repositorios,
  migraciones Flyway, seguridad (autenticación/autorización), multitenancy
  (incluye tests de acceso cruzado entre tenants) y controladores REST.
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

El informe de cobertura (JaCoCo) se genera en el build (`mvn verify`) y se
referenciará desde este documento a partir de la primera iteración que
incluya lógica de dominio (iteración 2 en adelante).
