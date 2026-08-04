# HANDOFF — A5 · Ola 1 · T140 Observabilidad

ADR de esta entrega: `docs/adr/ADR-0013-logs-estructurados-y-observabilidad.md`.
Sin migraciones Flyway (no tenía bloque reservado y no ha hecho falta ninguna).

---

## 1. Ficheros prohibidos: líneas que necesito que se apliquen

Nada de esto es **bloqueante** —todo funciona sin ello—, pero mejora el
resultado.

### `backend/pom.xml` — dependencia que NO he añadido

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**Por qué la pediría:** hoy las métricas solo se consultan una a una por
`/actuator/metrics`, que devuelve JSON de Actuator y no lo entiende ningún
sistema de scraping. Con el registro Prometheus habría un `/actuator/prometheus`
directamente consumible.

**Cómo lo he resuelto sin ella:** todas las métricas están registradas en el
`MeterRegistry` estándar (`SimpleMeterRegistry` en runtime), así que añadir el
exporter más adelante las publica todas sin tocar código. Solo habría que
añadir `prometheus` a `management.endpoints.web.exposure.include` —que vive en
`application.yml`, ver más abajo—.

No he necesitado ninguna librería de logging: Spring Boot 3.4+ trae
formateadores estructurados de serie y el proyecto está en 3.5.9.

### `application.yml` — dos líneas deseables

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus   # 'prometheus' solo si se añade el exporter
  endpoint:
    health:
      show-details: when-authorized          # hoy: never
      roles: TENANT_ADMIN, PLATFORM_ADMIN
```

**Contexto (importante):** las propiedades de un fichero importado con
`spring.config.import` tienen **menos** prioridad que las del documento que
declara el import. Es decir, `config/observability.yml` **no puede** sobrescribir
`management.endpoint.health.show-details: never`. Lo he resuelto sin tocar
`application.yml` publicando el detalle en un **grupo de salud propio**
(`/actuator/health/operations`, `show-details: when-authorized`), lo cual es
seguro y está probado. Si algún día se aplica el cambio de arriba, lo limpio
sería retirar el grupo `operations` de `config/observability.yml`.

### `docker-compose.prod.yml` — perfil de producción

Hoy `docker-compose.yml` fija `SPRING_PROFILES_ACTIVE: local` y el override de
producción no lo cambia, así que **producción arrancaría con el perfil `local`**
y, por tanto, con logs en texto plano en vez de JSON. Dos arreglos posibles:

```yaml
services:
  backend:
    environment:
      SPRING_PROFILES_ACTIVE: prod     # opción preferible
      # o, sin tocar perfiles:
      LOG_STRUCTURED_FORMAT: ecs
```

`LOG_STRUCTURED_FORMAT` funciona en cualquier perfil, así que es la salida de
emergencia si no se quiere tocar el perfil. Esto no es un problema mío que haya
introducido: es una consecuencia de que el override de producción sea mínimo.

---

## 2. Configuración

Fichero nuevo: **`backend/src/main/resources/config/observability.yml`**, ya
declarado en el `spring.config.import` de `application.yml`. No hace falta
añadir nada allí.

### Variables de entorno nuevas

| Variable | Por defecto | Qué hace |
|---|---|---|
| `LOG_STRUCTURED_FORMAT` | `ecs` (`` vacío en perfiles `local`/`test`) | Formato de log de consola: `ecs`, `logstash`, `gelf`, o vacío para texto plano legible |
| `OBSERVABILITY_OUTBOX_PENDING_THRESHOLD` | `1000` | Backlog de Outbox a partir del cual el health check pasa a `DEGRADED` |

### Propiedades nuevas

| Propiedad | Por defecto |
|---|---|
| `observability.outbox.pending-threshold` | `1000` |
| `logging.structured.format.console` | `ecs` (perfiles `local`/`test`: vacío) |
| `logging.pattern.console` | patrón con `%X{correlationId}`, `%X{tenantId}`, `%X{userId}`, `%X{useCase}`, `%X{result}` (solo `local`/`test`) |
| `management.endpoint.health.group.operations\|liveness\|readiness.*` | ver `config/observability.yml` |
| `management.endpoint.health.status.order` | `DOWN, OUT_OF_SERVICE, DEGRADED, UP, UNKNOWN` |
| `management.endpoint.health.status.http-mapping.DEGRADED` | `200` |
| `management.metrics.distribution.*` para `http.server.requests` | histograma + percentiles 50/95/99 + SLO 100ms/300ms/1s |

Puertos nuevos: ninguno. Servicios nuevos en compose: ninguno.

---

## 3. Métricas expuestas (para que A2 no las duplique)

Registradas por mí:

| Métrica | Tipo | Tags | Dónde |
|---|---|---|---|
| `jobs.executions` | contador | `job`, `result` | `shared.application.ScheduledJobRunner` |
| `jobs.duration` | timer | `job`, `result` | `shared.application.ScheduledJobRunner` |
| `notification.emails.sent` | contador | — | `notification.application.NotificationMetrics` |
| `notification.emails.failed` | contador | — | `notification.application.NotificationMetrics` |
| `notification.emails.skipped` | contador | — | `notification.application.NotificationMetrics` |
| `outbox.messages.dead` | gauge | — | `outbox.application.OutboxMetrics` (añadida) |

Ya existían: `outbox.messages.published`, `outbox.messages.retried`,
`outbox.messages.failed`, `outbox.messages.pending`, `outbox.publish.duration`.

De serie por Actuator (no hace falta registrar nada): `http.server.requests`
cubre **peticiones, errores y latencia** de RO-003. Solo he configurado
histograma y percentiles.

### A2 — logins fallidos y cuentas bloqueadas: **no implementadas por mí**

Son de tu épica (T30-03/04). Para que encajen con lo demás:

* Nómbralas `auth.logins.failed` y `auth.accounts.locked` (contadores).
* **No pongas el email ni el `tenantId` como tag**: alta cardinalidad y dato
  personal en el sistema de métricas (RS-014). Si necesitas desglose, usa un tag
  de motivo (`reason=BAD_CREDENTIALS|LOCKED|UNKNOWN_USER`).
* Sigue el patrón de `OutboxMetrics` / `NotificationMetrics`: un `@Component` en
  la capa `application` de tu módulo con el `MeterRegistry` inyectado.
* Si necesitas instrumentar el login con contexto de log, **el `useCase` ya sale
  solo**: `RequestObservabilityInterceptor` lo pone a partir del handler
  (`AuthController#login`). No añadas claves MDC nuevas: `ObservabilityContext`
  las rechaza a propósito (ver punto 5).

---

## 4. Eventos de integración

Ninguno. Esta épica no publica ni consume `eventType` alguno; no toco
`docs/integration/event-catalog.md`.

---

## 5. Cambios en ficheros compartidos y en módulos de otros

Todo aditivo, pero conviene que lo sepáis:

* **`shared/application/ObservabilityContext.java`** (nuevo). Declara las cinco
  únicas claves MDC permitidas y **rechaza cualquier otra** con
  `IllegalArgumentException`. Es deliberado: el formateador ECS vuelca el MDC
  entero al JSON, así que el MDC es el esquema del log. Si alguien necesita un
  campo nuevo en los logs, que lo añada aquí con su justificación, no por la
  puerta de atrás. `CorrelationIdFilter.MDC_KEY` sigue existiendo con el mismo
  valor, así que nada de lo que ya lo usaba se rompe.
* **`shared/application/ScheduledJobRunner.java`** (nuevo). Envoltorio para
  cualquier `@Scheduled`: correlación + métricas. **Si añadís una tarea
  programada en vuestra épica, usadlo**, o vuestras líneas de log saldrán sin
  `correlationId`.
* **`outbox`**: `OutboxPublisherJob` y `OutboxArchiverJob` ahora reciben
  `ScheduledJobRunner` en el constructor. `OutboxMessageRepository` gana
  `countFailed()` (implementado en el adaptador JPA). `OutboxMetrics` gana el
  gauge `outbox.messages.dead`.
* **`notification`** (será de B4 en la Ola 2): `SmtpEmailSender` recibe
  `NotificationMetrics` en el constructor y el `EmailSender` de fallback cuenta
  los envíos omitidos. Añadido `MailHealthIndicator`. Son cambios pequeños y
  aditivos, pero tocan tu módulo, B4.
* **`identity`** (A1/A2): **una sola línea, y no es funcionalidad mía.** El
  merge de la base V2 sobre `main` dejó
  `PlatformAdminBootstrapTest.InMemoryUserRepository` sin implementar
  `lockActiveAdmins(UUID)` —método que llegó por `main` desde
  `fix/backend-concurrencia-admin` mientras el test llegaba por la rama V2—. Sin
  ese `@Override` no compila el módulo de test, así que era imposible entregar
  nada. He añadido la implementación vacía y nada más. **Es muy probable que el
  worktree de A1 tenga el mismo conflicto pendiente**: conviene resolverlo una
  sola vez al integrar.

---

## 6. Qué he dejado fuera

* **T140-05 Panel técnico (P3, opcional): NO HECHO.** Requería trabajo de
  frontend (componente, ruta, guard, servicio y specs) y he preferido dejar
  T140-01..04 sólidos y probados. Cuando se aborde, la API ya está lista: el
  panel puede alimentarse de `/actuator/health/operations` (estado de Outbox y
  correo) y de `/actuator/metrics/{nombre}` (jobs fallidos, notificaciones
  fallidas). Último backup y última restauración probada dependen de T150 (A4) y
  hoy no hay ninguna fuente de datos para ellos.
* **Propagación del `correlationId` de la petición hasta el consumidor del
  Outbox.** El job tiene correlación propia, pero no la de la petición que
  originó el evento. Hacerlo bien exige una columna `correlation_id` en
  `outbox_message`, es decir, una migración Flyway; esta épica no tiene bloque
  reservado. **Recomendación:** que lo asuma quien tenga bloque libre en la Ola 2
  (una columna `NULL`-able y rellenarla en `OutboxWriter` desde
  `ObservabilityContext.currentCorrelationId()`).
* **Métricas de login fallido y cuentas bloqueadas**: de A2, por reserva
  explícita.
* **Exporter Prometheus**: ver punto 1.
* **`docs/api/openapi.yaml`**: no lo he tocado (prohibido). No he añadido
  endpoints REST propios —los de Actuator no los documenta springdoc—, así que
  no hay nada que reexportar por mi parte.

---

## 7. Riesgos y cosas asumidas

* **`DEGRADED` es un estado no estándar.** Responde HTTP 200 a propósito, para
  que un Outbox atascado o un SMTP caído se vean sin que el orquestador reinicie
  un contenedor que atiende peticiones con normalidad. Una herramienta externa
  que solo entienda UP/DOWN lo leerá como «no UP»; el código HTTP, que es lo que
  miran las sondas, sigue siendo 200. Razonado en el ADR.
* **Las peticiones que mueren antes del `DispatcherServlet`** (401 del resource
  server, 429 del rate limit, 413 por tamaño) llevan `correlationId` pero no
  `tenantId`/`userId`: en ese punto todavía no hay principal. Es una limitación
  aceptada, no un olvido.
* **`StructuredLoggingIntegrationTest` no captura la consola.** Spring Boot
  inicializa el sistema de logging **una sola vez por JVM**, así que en una suite
  completa gana la configuración del primer contexto que arranca y un test que
  afirmase sobre `System.out` pasaría o fallaría según el orden de ejecución (me
  pasó). El test engancha el propio `StructuredLogEncoder` de Boot a un appender
  de memoria: comprueba exactamente el JSON que produciría producción, sin
  depender del orden.
