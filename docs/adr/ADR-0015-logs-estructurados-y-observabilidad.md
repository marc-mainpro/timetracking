# ADR-0015: Logs estructurados, correlación y sondas de salud

* Estado: accepted
* Fecha: 2026-08-04
* Épica: T140 (RNF-019, RNF-020, RO-001, RO-002, RO-003, RS-014)

## Contexto y problema

El `X-Correlation-Id` ya existía: `CorrelationIdFilter` lo genera si falta, lo
devuelve en la respuesta, lo publica en el MDC y de ahí lo recogen los Problem
Details (ADR-0006) y la tabla de auditoría. Pero **no aparecía en ningún log**:
no había `logging.pattern.*`, ni `logback-spring.xml`, ni formato estructurado.
Un identificador de correlación que no está en la traza sirve para poco: el
cliente lo cita en la incidencia y quien investiga no puede buscar por él.

Tres huecos más, del mismo tipo:

* Las tareas programadas (publicador y archivador del Outbox) corrían **sin**
  `correlationId`: no nacen de una petición HTTP, así que el filtro nunca se
  ejecuta para ellas y no había forma de agrupar en una traza lo que hizo una
  tanda concreta.
* No había ningún `HealthIndicator` propio, y `/actuator/health` estaba en
  `show-details: never`, así que respondía solo `{"status":"UP"}`. Si el Outbox
  se atascaba o el SMTP dejaba de responder, la sonda seguía en verde.
* De métricas solo existían las del Outbox. Nada de peticiones, errores,
  latencia, notificaciones ni jobs.

Dos restricciones condicionan la solución:

1. **No se pueden añadir dependencias** (`backend/pom.xml` es fichero
   compartido y las dependencias de la V2 se cerraron en la Ola 0). Nada de
   `logstash-logback-encoder` ni de `micrometer-registry-prometheus`.
2. **`application.yml` no se puede editar**, y las propiedades de un fichero
   importado con `spring.config.import` tienen **menos** prioridad que las del
   documento que declara el import. `config/observability.yml` puede añadir
   configuración nueva, pero **no puede sobrescribir**
   `management.endpoint.health.show-details: never`.

## Decisión

### 1. Formato de log: ECS nativo de Spring Boot, JSON por defecto

Spring Boot 3.4+ trae formateadores estructurados de serie
(`logging.structured.format.console`), sin dependencias adicionales. Se elige
**ECS** (Elastic Common Schema) porque vuelca el MDC entero como **campos planos
de primer nivel** del JSON: `correlationId`, `tenantId`, `userId`, `useCase` y
`result` quedan consultables sin parsear el mensaje. `logstash` y `gelf`
también están disponibles y se pueden seleccionar con `LOG_STRUCTURED_FORMAT`
sin tocar código.

El **valor por defecto es JSON**, es decir, producción por defecto: un
despliegue que se olvide de configurar el formato queda observable, en vez de
descubrirlo durante el primer incidente. Los perfiles `local` y `test` lo
desactivan en un documento propio de `config/observability.yml` y usan un patrón
de consola legible con los mismos campos entre corchetes:

```
2026-08-04T10:15:00.123+02:00  INFO [cid=… tenant=… user=… useCase=… result=…] c.t.t.…
```

Descartado escribir un `logback-spring.xml` propio: haría el mismo trabajo con
más código que mantener y perdiendo la integración con las propiedades
`logging.*` de Boot.

### 2. Esquema de log cerrado (`ObservabilityContext`)

Como el formateador vuelca el MDC entero, **el MDC es de facto el esquema del
log**. `ObservabilityContext` (en `shared.application`, sin Spring) declara las
cinco únicas claves permitidas —las de RO-002— y `put` **rechaza cualquier otra**.
Un descuido del tipo «meto la cookie en el MDC mientras depuro» falla en tiempo
de ejecución y en test, en vez de publicarse en cada línea de la petición
(RS-014).

Vive en `application` y no en `infrastructure` porque lo consumen tanto el filtro
HTTP como el runner de jobs, y la arquitectura por capas prohíbe que nadie
dependa de `infrastructure`.

### 3. Los campos que dependen de la autenticación se ponen en un interceptor

`tenantId`, `userId` y `useCase` no puede ponerlos un filtro colocado al
principio de la cadena: cuando ese filtro corre todavía no se ha autenticado a
nadie ni se ha resuelto qué controlador atenderá la petición. Por eso
`RequestObservabilityInterceptor` es un `HandlerInterceptor` de Spring MVC, que
se ejecuta ya dentro del `DispatcherServlet`.

Se registra vía `WebMvcConfigurer`, que es un punto de extensión aditivo, y no
como filtro nuevo: el orden de la cadena de filtros es una decisión global que
vive en `SecurityConfig` y no admite contribuciones (ADR-0011).

**Consecuencia asumida:** las peticiones que mueren antes del dispatcher (401
del resource server, 429 del rate limit, 413 por tamaño) llevan `correlationId`
pero no `tenantId`/`userId` —que en esos casos, precisamente, aún no existen—.

### 4. Correlación en las tareas programadas (`ScheduledJobRunner`)

Cada ejecución de un `@Scheduled` recibe su propio `correlationId` y su
`useCase` (`job:<nombre>`), y el contexto se limpia al terminar para que el hilo
del pool no lo arrastre a la ejecución siguiente. El mismo runner cuenta
`jobs.executions` y `jobs.duration` (RO-003).

**No** se propaga el `correlationId` de la petición que originó el evento hasta
el consumidor del Outbox: eso exigiría una columna nueva en `outbox_message`, y
esta épica no tiene bloque de migraciones Flyway reservado. Queda registrado en
el `HANDOFF.md`.

### 5. Health checks: sonda pública sin detalle, grupo operativo con detalle

`/actuator/health` es público porque lo consulta el healthcheck de Docker
Compose antes de que exista ninguna credencial, y `application.yml` lo deja en
`show-details: never` —que este ADR no puede cambiar, ver restricción 2—.

La tensión «los checks no sirven sin detalle / el detalle no puede ser público»
se resuelve **sin tocar ficheros prohibidos**, con un grupo de salud nuevo:

| Endpoint | Contenido | Acceso |
|---|---|---|
| `/actuator/health` | solo estado agregado | público (sonda del contenedor) |
| `/actuator/health/liveness` | `ping` | público, solo estado |
| `/actuator/health/readiness` | `db` | público, solo estado |
| `/actuator/health/operations` | todos los checks con detalle | `when-authorized`, roles `TENANT_ADMIN` y `PLATFORM_ADMIN` |

`management.endpoint.health.group.operations.show-details` es una clave que
`application.yml` no fija, así que sí puede declararse en el fichero importado.
Un anónimo que llame a `/actuator/health/operations` recibe únicamente
`{"status":…}`: umbrales, host SMTP y tamaño del backlog dejan de ser
información pública sin necesidad de abrir ni cerrar ninguna ruta.

### 6. Estado propio `DEGRADED`, mapeado a HTTP 200

Actuator solo trae `UP`, `DOWN`, `OUT_OF_SERVICE` y `UNKNOWN`, y `DOWN` responde
503. Como `/actuator/health` es la sonda del contenedor, con ese vocabulario la
única forma de señalar «hay algo que mirar» es provocar el reinicio de una
aplicación que funciona —y reiniciar no publica ni un mensaje de Outbox más, ni
levanta el servidor de correo ajeno—.

Se añade `DEGRADED`, ordenado entre `OUT_OF_SERVICE` y `UP` pero **mapeado a
HTTP 200**. Reparto de estados:

| Indicador | UP | DEGRADED | DOWN | UNKNOWN |
|---|---|---|---|---|
| `ping` (aplicación) | siempre | — | — | — |
| `db` (PostgreSQL, de serie) | conexión válida | — | sin conexión | — |
| `outbox` | backlog bajo umbral y 0 `FAILED` | backlog alto o algún `FAILED` | no se puede ni consultar la tabla | — |
| `mail` | SMTP acepta la conexión | SMTP no responde | — | `mail.enabled=false` |

`DOWN` queda reservado para lo que hace la aplicación inservible. El correo
deshabilitado devuelve `UNKNOWN` con `enabled=false`: está apagado a propósito
—es el valor por defecto del proyecto—, no roto, y con el orden de estados
configurado `UP` gana a `UNKNOWN`, así que no arrastra el agregado. Marcarlo
`DOWN` dejaría la sonda en rojo permanente en la configuración por defecto, que
es la mejor forma de conseguir que nadie la mire nunca.

### 7. Métricas sin exporter

Peticiones, errores y latencia salen del timer `http.server.requests` que
Actuator instrumenta de serie (tags `uri`, `method`, `status`, `outcome`): solo
se configuran histograma, percentiles y SLO. Se añaden
`notification.emails.{sent,failed,skipped}`, `jobs.{executions,duration}` y el
gauge `outbox.messages.dead`.

**No hay exporter Prometheus**: `micrometer-registry-prometheus` no está en el
`pom.xml` y no se pueden añadir dependencias. Las métricas se consultan por
`/actuator/metrics`, que exige autenticación. Declarado en el `HANDOFF.md`.

Las métricas de **logins fallidos y cuentas bloqueadas** (RO-003) las implementa
la épica T30-03/04, no esta.

## Consecuencias

* (+) Una incidencia se investiga buscando un solo `correlationId`: la petición,
  sus errores y la entrada de auditoría comparten identificador, y ahora también
  las líneas de log.
* (+) Los logs son agregables por máquina en producción y legibles por humanos
  en desarrollo, sin dependencias nuevas ni ficheros de logback que mantener.
* (+) El esquema de log cerrado convierte RS-014 en un fallo detectable, no en
  una convención que se recuerda o no.
* (+) Un Outbox atascado o un SMTP caído son visibles en la sonda operativa sin
  que el orquestador recicle contenedores sanos.
* (−) `DEGRADED` es un estado no estándar: una herramienta externa que solo
  entienda UP/DOWN lo verá como «no UP». A cambio, el código HTTP sigue siendo
  200, que es lo que miran las sondas.
* (−) El detalle de salud está en `/actuator/health/operations`, no en
  `/actuator/health`. Si en el futuro se puede editar `application.yml`, lo
  limpio sería poner `show-details: when-authorized` en el endpoint y retirar el
  grupo.
* (−) Sin exporter Prometheus, las métricas se leen a mano o vía un agente que
  consuma `/actuator/metrics` con credenciales.
* (−) El `correlationId` de la petición no llega al consumidor del Outbox: la
  publicación de un evento y la petición que lo originó se correlacionan por
  `aggregateId` y marca temporal, no por identificador.

## Alternativas descartadas

* **`logstash-logback-encoder`**: es la opción habitual, pero exige dependencia
  nueva. El formateador nativo de Boot 3.4+ cubre el requisito.
* **`logback-spring.xml` propio**: control total sobre los appenders a cambio de
  duplicar lo que Boot ya hace y perder la integración con `logging.*`.
* **`show-details: always`** en `/actuator/health`: publicaría umbrales, host
  SMTP y estado interno a cualquiera, porque la ruta es pública.
* **Cerrar `/actuator/health` con autenticación**: rompería el healthcheck de
  `docker-compose.yml`, que no tiene credenciales y es fichero prohibido.
* **Que `outbox` y `mail` devuelvan `DOWN`**: convertiría un problema operativo
  en un reinicio en bucle del contenedor.
* **`ThreadLocal` propio en vez de MDC**: obligaría a copiar el contexto al
  formateador a mano; el MDC ya está integrado con SLF4J, con ECS y con el
  patrón de consola.
