# Funcionalidades del proyecto

Resumen funcional de TFP, el SaaS multitenant de control horario. Para el detalle
requisito → tarea → caso de uso → endpoint → prueba, ver
`docs/traceability/requirements-matrix.md`.

## Visión general

Monolito modular con Spring Boot 3.5.9 / Java 21 / PostgreSQL y frontend Angular 19,
con 12 módulos de dominio (`identity`, `tenant`, `timetracking`, `corrections`,
`calendar`, `shift`, `absence`, `reporting`, `notification`, `audit`, `outbox`,
`shared`) y unos 60 endpoints REST bajo `/api/v1`.

Tres actores: `PLATFORM_ADMIN` (gestiona organizaciones), `TENANT_ADMIN`
(administra su organización) y `EMPLOYEE`.

## 1. Administración de plataforma

- Ciclo de vida completo de tenants: creación, activación, suspensión (con motivo
  obligatorio), reactivación y archivado; cada transición queda auditada con
  estado anterior y posterior.
- Listado paginado con número de usuarios y último acceso, y detalle por tenant.
- Un tenant inactivo o suspendido no puede operar: se bloquea en login, en refresh
  y en toda petición autenticada.
- Panel técnico de estado (`GET /api/v1/platform/system-status`): colas de outbox y
  notificaciones atascadas, con indicador de «necesita atención».
- Auditoría de plataforma consultable y filtrable por acción y rango de fechas.

## 2. Alta pública de organizaciones (tres pasos)

Solicitud → verificación por correo → aprobación desde plataforma. Un tenant nunca
nace operativo de un solo paso.

- Reenvío de verificación limitado.
- Throttling por IP (hasheada) y por correo.
- Mensajes anti-enumeración idénticos en los tres endpoints públicos.
- Desactivable por configuración (`registration.public.enabled=false`).
- Bandeja de solicitudes en plataforma con aprobación y rechazo motivado.

## 3. Autenticación y seguridad

- JWT de vida corta más refresh token rotatorio en cookie `HttpOnly`, con detección
  de reutilización.
- Gestión de sesiones: listar las activas, revocar una o revocarlas todas.
- Recuperación de contraseña mediante token de un solo uso.
- Bloqueo temporal de cuenta tras intentos fallidos: umbral y duración
  configurables, reinicio al acierto, aislado por tenant e indistinguible de una
  cuenta inexistente.
- Rate limiting por ruta en los endpoints sensibles (429 `RATE_LIMIT_EXCEEDED`).
- Email único global para eliminar la ambigüedad en el login (ADR-0008).
- Multitenancy por `tenantId` derivado del principal autenticado, nunca de la
  petición.

## 4. Gestión de empleados

Listado paginado con filtro por estado, alta, edición, activación y desactivación, y
asignación de roles. `PLATFORM_ADMIN` no es asignable dentro de un tenant.

## 5. Control horario

- Fichaje: iniciar jornada, iniciar y finalizar pausa, cerrar jornada, siempre con
  la hora del servidor.
- Invariantes: una sola jornada abierta por empleado, una sola pausa abierta por
  jornada, no se cierra con una pausa abierta y no se re-cierra una jornada cerrada.
- Consulta de la jornada actual, historial propio paginado por rango y vista de
  administración filtrable por empleado y fechas.
- Reglas horarias configurables por tenant: jornada máxima diaria, descanso
  obligatorio, paso de redondeo y tolerancia.
- Evaluación automática al cerrar o ajustar una jornada: duración esperada frente a
  la trabajada, tiempo efectivo, pausado, horas extra, desviación y anomalías
  (`MAX_DAILY_WORK_EXCEEDED`, `REQUIRED_BREAK_NOT_MET`).

## 6. Correcciones de jornada

Los cambios históricos solo entran por corrección aprobada. El empleado propone un
nuevo inicio y fin y hasta 24 pausas, con motivo; el administrador aprueba o rechaza
con comentario. Solo cabe una solicitud pendiente por jornada y usuario. La
aprobación aplica los cambios, deja la jornada en `ADJUSTED`, dispara la
reevaluación y genera un registro de auditoría.

## 7. Calendarios laborales

Calendarios con reglas por día de la semana, festivos, jornadas especiales, zona
horaria IANA propia y vigencia (`validFrom` y `validTo` inclusivos).

- Asignables por tenant, equipo o empleado.
- Resolución del calendario efectivo: gana la asignación más específica
  (empleado > equipo > tenant); un calendario archivado o caducado no bloquea su
  ámbito, se cae al siguiente menos específico (ADR-0017).
- Archivado lógico: un calendario archivado no admite edición ni nuevas
  asignaciones.
- La jornada esperada no varía porque el día civil dure 23 o 25 horas por el cambio
  de horario estacional.

## 8. Turnos

Plantillas de turno (inicio, fin y pausa planificada), incluidas las que cruzan
medianoche, asignables a empleados con vigencia. El turno prevalece sobre el
calendario como tiempo previsto y alimenta la comparación previsto-real de la
evaluación. El empleado consulta sus turnos por fecha.

## 9. Ausencias

Catálogo de tipos sembrado automáticamente al crear el tenant, con marcas de
aprobación requerida y adjunto permitido. El empleado solicita por rango con motivo,
sigue el estado y puede cancelar mientras la solicitud siga pendiente; el
administrador aprueba o rechaza con comentario. Las ausencias aprobadas afectan al
tiempo esperado de la jornada.

## 10. Informes

- Resumen diario propio del empleado y resumen agregado por empleado para el
  administrador: trabajado, pausado, esperado, efectivo, horas extra, desviación y
  número de jornadas totales, ajustadas, abiertas y evaluadas.
- Exportación CSV (UTF-8 sin BOM, RFC 4180) y PDF (PDFBox). Ambas identifican al
  empleado por apellidos y nombre en lugar de por UUID.

## 11. Notificaciones

Notificaciones internas con contador de no leídas y marcado de leído, más envío por
correo. Todo asíncrono mediante Transactional Outbox sin broker (ADR-0005), con
consumidores idempotentes (clave `eventId` más consumidor), reintentos con máximo
configurable y trazabilidad completa del envío: estado, intentos, último error y
fechas.

## 12. Auditoría y operación

- Auditoría de tenant paginada y filtrable (ajustes de jornada, turnos, aprobaciones
  y rechazos) con metadata y correlation ID.
- Logs estructurados en formato ECS con correlation ID propagado en
  `X-Correlation-Id`, y esquema cerrado que garantiza que no se filtran contraseñas,
  tokens ni cookies.
- Métricas de peticiones, latencia, autenticación, outbox, notificaciones y jobs;
  health checks de base de datos, outbox y correo.
- Backup diario cifrado AES-256 con rotación *grandfather-father-son* y
  procedimiento de restauración probado en simulacro real (RPO 24 h, RTO 1 h
  declarados).

## Calidad

ArchUnit verifica en cada build que el dominio no dependa de Spring, que los
controladores no toquen repositorios, que no haya ciclos entre módulos y que los
endpoints privilegiados lleven autorización. Cobertura de backend con Testcontainers,
pruebas de componente en frontend y 20 casos E2E de navegador con Playwright sobre la
pila de Docker Compose. 17 ADR documentan las decisiones estructurales.

## Fuera de alcance (decisión, no omisión)

MFA, SSO, API pública para terceros, broker de mensajería, facturación, alta
disponibilidad, escalado horizontal, Kubernetes, event sourcing y CQRS completo.
Descartados en `requisitos-v2-control-horario.md` §11.
