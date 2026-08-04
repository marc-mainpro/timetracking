# Manual de operación

## Arranque y parada

- Local/demo: `cp .env.example .env && docker compose up -d --build`
- Parada: `docker compose down`
- Limpieza completa: `docker compose down -v`

## Variables de entorno clave

- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `JWT_SECRET`
- `FRONTEND_ORIGIN`
- `AUTH_REFRESH_COOKIE_SECURE`
- `APP_REQUEST_MAX_PAYLOAD_BYTES`
- `LOG_STRUCTURED_FORMAT` (formato de log: `ecs` por defecto; vacío = texto plano)
- `OBSERVABILITY_OUTBOX_PENDING_THRESHOLD` (backlog a partir del cual el health
  check del Outbox pasa a `DEGRADED`; 1000 por defecto)

## Backups PostgreSQL

- Backup lógico: `docker compose exec postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > backup.sql`
- Restore: `docker compose exec -T postgres psql -U "$POSTGRES_USER" "$POSTGRES_DB" < backup.sql`

## Auditoría

- Endpoint: `GET /api/v1/admin/audit-events`
- Acceso: solo `TENANT_ADMIN`
- Uso típico: validación de aprobaciones/rechazos de correcciones.

## Outbox FAILED

- Identificar mensajes: consultar tabla `outbox_message` por `status = 'FAILED'`.
- Acción manual: reintentar desde aplicación invocando el caso de uso
  `RetryFailedOutboxMessage` o mediante operación técnica equivalente.
- Referencia funcional: `docs/integration/outbox-publisher.md`.

## Health checks

Cuatro comprobaciones (RO-001, ADR-0013): aplicación (`ping`), PostgreSQL
(`db`), Outbox (`outbox`) y correo saliente (`mail`).

| Endpoint | Contenido | Acceso |
|---|---|---|
| `/actuator/health` | solo estado agregado | público — es la sonda de Docker |
| `/actuator/health/liveness` | `ping` | público, solo estado |
| `/actuator/health/readiness` | `db` | público, solo estado |
| `/actuator/health/operations` | **todos los checks con detalle** | Bearer JWT de `TENANT_ADMIN` o `PLATFORM_ADMIN` |

Para diagnosticar, usar siempre el grupo operativo:

```
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/actuator/health/operations
```

Estados y qué hacer con cada uno:

- `UP`: nada.
- `DEGRADED` (estado propio, responde **HTTP 200** a propósito para no provocar
  el reinicio de un contenedor sano): hay backlog de Outbox por encima del
  umbral, mensajes `FAILED` esperando intervención, o el SMTP no responde. Ver
  «Outbox FAILED» más abajo y revisar el servidor de correo.
- `DOWN`: la aplicación no puede servir; normalmente PostgreSQL inaccesible.
  Responde 503 y el orquestador reciclará el contenedor.
- `UNKNOWN` en `mail`: `mail.enabled=false`. El correo está apagado a propósito
  (valor por defecto), no roto.

## Métricas

- Endpoint: `/actuator/metrics` (requiere autenticación). No hay exporter
  Prometheus: la dependencia `micrometer-registry-prometheus` no está en el
  `pom.xml`.
- Peticiones, errores y latencia: `http.server.requests` (tags `uri`, `method`,
  `status`, `outcome`; con histograma y percentiles 50/95/99).
- Outbox: `outbox.messages.published`, `outbox.messages.retried`,
  `outbox.messages.failed` (contador acumulado), `outbox.messages.pending`
  (gauge de backlog), `outbox.messages.dead` (gauge de `FAILED` pendientes de
  intervención) y `outbox.publish.duration`.
- Notificaciones: `notification.emails.sent`, `notification.emails.failed`,
  `notification.emails.skipped` (envíos omitidos por `mail.enabled=false`; si
  este sube en producción, el correo está mal configurado).
- Tareas programadas: `jobs.executions` y `jobs.duration`, con tags `job`
  (`outbox-publisher`, `outbox-archiver`) y `result` (`SUCCESS`/`FAILURE`).

## Logs

- Formato estructurado JSON (ECS) por defecto; los perfiles `local` y `test`
  usan un patrón de consola legible (RNF-019, ADR-0013).
- Campos por línea (RO-002): `@timestamp`, `log.level`, `correlationId`,
  `tenantId`, `userId`, `useCase` y `result`.
- Para investigar una incidencia, buscar por el `correlationId` que el cliente
  recibió en la cabecera `X-Correlation-Id`: la petición, sus errores y la
  entrada de auditoría comparten identificador. Las tareas programadas generan
  el suyo propio y salen con `useCase=job:<nombre>`.
- **Nunca se registran contraseñas, tokens, cookies ni cuerpos de correo**
  (RS-014). El conjunto de campos es cerrado por código
  (`ObservabilityContext`) y lo vigila `NoSecretsInLogsTest`.

## Problemas comunes

- Login no refresca en local: revisar `AUTH_REFRESH_COOKIE_SECURE=false`.
- Frontend no alcanza la API: comprobar que `frontend` esté healthy y que nginx
  haga proxy a `backend:8080`.
- 413 en peticiones grandes: revisar `APP_REQUEST_MAX_PAYLOAD_BYTES` y
  `client_max_body_size` de nginx.
