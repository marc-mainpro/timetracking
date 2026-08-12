# Componentes (C4 — Nivel 3, backend)

Paquete base: `com.tfp.timetracking`. Módulos (paquetes de primer nivel):

- `identity`: usuarios, autenticación, roles, sesiones, bloqueo de cuenta y
  recuperación de contraseña.
- `tenant`: organizaciones, su ciclo de vida y las solicitudes de alta.
- `timetracking`: jornadas (Workday), pausas (BreakEntry), reglas horarias y
  evaluación de la jornada.
- `calendar`: calendarios laborales, festivos, jornadas especiales y
  asignación por ámbito (tenant, equipo o empleado).
- `absence`: tipos de ausencia y solicitudes con su resolución.
- `shift`: plantillas de turno y asignaciones, incluidos los nocturnos.
- `corrections`: solicitudes de corrección sobre jornadas.
- `reporting`: informes de horas trabajadas.
- `notification`: avisos al usuario y su envío por correo con reintentos.
- `audit`: registro de auditoría de operaciones sensibles.
- `outbox`: mensajes de integración transaccionales.
- `shared`: utilidades comunes (errores, tipos de dominio compartidos).

En `shared.application` residen además los puertos transversales usados por
varios módulos: `TenantContext` (tenant, usuario y roles resueltos desde el
principal autenticado), `AuthenticatedPrincipalStateChecker`,
`IntegrationEventMapper` y `TenantUsageQuery`.

`reporting` define además `EmployeeDirectoryPort` en su propio `domain`, y
`UserEmployeeDirectoryAdapter` lo implementa sobre `identity.domain.UserRepository`.
Es el único punto donde `reporting` cruza hacia `identity`, y solo lo usan los
formateadores de salida (CSV, PDF), que necesitan un empleado legible: la
agregación de tiempo (`TenantEmployeeSummary`) sigue sin conocer nombres. El
puerto vive en el módulo consumidor porque `identity` no depende de
`reporting`, así que no se cierra ningún ciclo.

Que un puerto viva en `shared` no es una decisión estética: cuando los dos
módulos que conecta ya se conocen en un sentido —`tenant` depende de `identity`
para crear el primer administrador—, declarar el puerto en el módulo consumidor
obligaría al otro a mirar hacia atrás y cerraría un ciclo. Es el criterio de
ADR-0011 y lo verifica `ModuleCyclesTest`.

Cada módulo se organiza en capas, de dentro hacia fuera:

```text
<módulo>/
├── domain/            # entidades, invariantes, eventos de dominio, puertos
├── application/        # casos de uso, orquestación de dominio
├── infrastructure/      # adaptadores: JPA, seguridad, outbox, clock, etc.
└── interfaces.rest/     # controladores REST, DTO de API
```

## Reglas de dependencia (verificadas por ArchUnit)

- `domain` no importa Spring, JPA ni `application`/`infrastructure`/`interfaces`.
- Los controladores no contienen lógica de negocio ni acceden a
  repositorios directamente; delegan en casos de uso de `application`.
- `infrastructure` implementa puertos definidos en `domain`/`application`.
- El tenant operativo se resuelve desde el principal autenticado, nunca desde
  DTOs o parámetros del cliente.
- Convención tenant-aware: los métodos públicos de puertos de repositorio de
  negocio reciben `tenantId` como primer parámetro. Excepciones documentadas:
  `TenantRepository` (su clave es el propio tenant) y métodos globales de
  autenticación/registro estrictamente necesarios mientras existan flujos sin
  `TenantContext` previo (`UserRepository.findByEmail/existsByEmail/findById(id)`).
- Sin ciclos de dependencia entre módulos.
- Entidades JPA separadas del modelo de dominio y de los DTO de API.

En `timetracking`, el agregado `Workday` se persiste completo junto a sus
`BreakEntry` como una unidad con `@Version` para bloqueo optimista.

En `corrections`, `CorrectionRequest` tambien usa `@Version` y se resuelve en
la misma transaccion que el ajuste de `Workday` y la escritura de auditoria.

## Estrategia de concurrencia

- `Workday` y `CorrectionRequest` usan bloqueo optimista con columna `version`.
- `GlobalExceptionHandler` traduce `ObjectOptimisticLockingFailureException` y
  `OptimisticLockException` a HTTP `409` con `errorCode = CONCURRENT_MODIFICATION`.
- El arranque de jornada tiene una segunda red en base de datos:
  `ux_workday_active` impide dos jornadas activas para el mismo
  `tenant_id + employee_id` incluso si dos requests pasan la comprobacion de
  negocio a la vez.
- Esa violacion de unicidad tambien se traduce a HTTP `409`; para la jornada
  activa el `errorCode` es `WORKDAY_ALREADY_OPEN`.
- Las pruebas de carrera de `T604` fuerzan colisiones reales con barreras en
  los puertos de repositorio, sin `sleep`, para validar:
  - doble cierre simultaneo de jornada
  - doble aprobacion simultanea de la misma correccion
  - doble `start` simultaneo de jornada
  - carrera entre `startBreak` y `endWorkday`

## Vista de proceso

Este documento describe la estructura: qué módulos hay y qué contiene cada uno.
El recorrido de cada operación de negocio a través de esos módulos —en qué orden
ocurren las cosas y qué caminos alternativos hay— está en `docs/procesos/`, con
diagramas de secuencia y máquinas de estado.
