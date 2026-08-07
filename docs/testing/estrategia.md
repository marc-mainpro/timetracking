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

## End-to-end de navegador (T160-01)

`frontend/e2e/` con Playwright, ejecutados con `npm run e2e` contra la **pila
completa** levantada por Docker Compose: frontend, backend, PostgreSQL y
mailpit. No se simula el backend a propósito: el valor de estas pruebas está en
atravesar todas las capas, y un backend simulado no detectaría una migración
rota ni una fuga entre tenants.

Requisitos para ejecutarlos: `docker compose up -d --build`,
`PUBLIC_REGISTRATION_ENABLED=true` y `PLATFORM_ADMIN_EMAIL` /
`PLATFORM_ADMIN_PASSWORD`.

| Fichero | Flujos cubiertos |
|---|---|
| `registro-publico.spec.ts` | Solicitud de alta, verificación por correo real (leído de mailpit), aprobación y activación desde plataforma, primer acceso del propietario; y respuesta anti-enumeración ante correo repetido |
| `jornada.spec.ts` | Inicio, pausa, fin y evaluación de jornada; invariante de jornada única abierta; informe de tenant con las jornadas contabilizadas |
| `aislamiento.spec.ts` | Fuga entre tenants en listados y por identificador (404, no 403), empleado contra administración, administrador contra plataforma, y suspensión/reactivación de tenant |
| `correccion.spec.ts` | Solicitud, aprobación con reevaluación de la jornada, rechazo que no la altera, y doble resolución rechazada (RT-006) |
| `calendario-turno.spec.ts` | Creación y asignación de calendario por ámbito, turno nocturno que cruza medianoche, y turno como tiempo previsto de la jornada (T90-06) |
| `sesiones.spec.ts` | Apertura de sesión por login, aislamiento entre usuarios, revocación masiva que deja el token inservible y revocación individual que no afecta a las demás |
| `ausencia-notificacion.spec.ts` | Solicitud, aprobación y rechazo de ausencia con la notificación que generan a través del Outbox, lectura del aviso y aislamiento de notificaciones entre tenants |

Se ejecutan con un solo worker: comparten base de datos y varias afirman sobre
listados completos, así que el paralelismo las volvería dependientes del orden.
Las llamadas de autenticación reparten el `X-Forwarded-For` entre direcciones de
documentación para no chocar con el rate limiting, que sigue igual de estricto.

Los recorridos que atraviesan el Outbox —notificaciones y el sembrado del
catálogo de ausencias— esperan con `expect.poll` en lugar de con una pausa fija:
la entrega es eventual y su latencia depende del intervalo de sondeo.
