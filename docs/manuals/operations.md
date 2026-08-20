# Manual de operación

## Arranque y parada

- Local/demo: `cp .env.example .env && docker compose up -d --build`
- Parada: `docker compose down`
- Limpieza completa: `docker compose down -v`

## Despliegue en Railway

Producción son dos servicios de Railway, `timetracking-backend` y
`timetracking-frontend`, que consumen las imágenes publicadas en GHCR con la
etiqueta `latest`.

El despliegue es automático y va encadenado a la publicación de imágenes:

1. Se publica un tag `v*` y `ghcr.yml` construye y sube las dos imágenes.
2. Al terminar **correctamente**, `railway-redeploy.yml` se dispara por
   `workflow_run` y redespliega los dos servicios con
   `railway redeploy --from-source`, que vuelve a resolver la etiqueta y baja
   la imagen recién publicada. Un `redeploy` sin `--from-source` reutilizaría la
   imagen ya fijada y produciría un despliegue sin cambios.
3. El backend se redespliega antes que el frontend, que le hace `proxy_pass`.

### Qué hace falta configurar

| Dónde | Nombre | Valor |
| --- | --- | --- |
| Secreto del repositorio | `RAILWAY_TOKEN` | Token **de proyecto** de Railway, creado desde los ajustes del proyecto para el entorno de producción. |
| Variable del repositorio (opcional) | `RAILWAY_ENVIRONMENT` | Entorno de Railway al que desplegar. Por defecto `production`. |

El workflow falla de forma explícita si `RAILWAY_TOKEN` no está configurado, en
vez de dejar un servicio a medio desplegar con un error de autenticación.

### Rollback

Republicar el tag anterior no basta: `latest` ya apunta a la versión nueva. Para
volver atrás, redesplegar desde Railway la versión anterior del servicio, o
apuntar el servicio a la etiqueta semántica concreta (`1.2.3`) y redesplegar.

### Despliegue manual

Con el token de proyecto a mano:

```bash
RAILWAY_TOKEN=xxx npx -y @railway/cli@5 redeploy \
  --service timetracking-backend --environment production --from-source --yes
```

## Variables de entorno clave

### Base de datos y aplicación

- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `JWT_SECRET`
- `FRONTEND_ORIGIN`
- `AUTH_REFRESH_COOKIE_SECURE`
- `AUTH_PASSWORD_RESET_URL_TEMPLATE` y `REGISTRATION_VERIFICATION_URL`: enlaces
  que se incluyen en los correos de recuperación de contraseña (RF-USR-006) y de
  verificación del alta (RF-REG-004). En `docker-compose.yml` se derivan de
  `FRONTEND_ORIGIN`, así que basta con apuntar esa variable al dominio público.
  **Hay que indicarlos explícitamente**: el correo lo compone el job del outbox,
  fuera de toda petición HTTP, así que no hay cabecera `Host` de la que deducir
  el dominio; deducirlo de ella, además, permitiría envenenar el enlace con una
  cabecera falsa. Deben conservar un único `%s`, que es donde va el token.
  Si se dejan sin definir, ambos correos enviarían enlaces a `localhost:4200`.
- `NOTIFICATION_APP_BASE_URL`: base absoluta del frontend con la que se compone
  el enlace de cada notificación por correo (T170-09). Mismo motivo que las
  anteriores: el correo se compone en un job, fuera de toda petición HTTP. A
  diferencia de ellas **no lleva `%s`** —la ruta la aporta cada notificación en
  su `actionPath`— y **no es obligatoria**: si se deja vacía, el correo se envía
  igual, solo que sin enlace. Debe apuntar al dominio público del frontend, sin
  barra final.
- `APP_REQUEST_MAX_PAYLOAD_BYTES`
- `LOG_STRUCTURED_FORMAT` (formato de log: `ecs` por defecto; vacío = texto plano)
- `OBSERVABILITY_OUTBOX_PENDING_THRESHOLD` (backlog a partir del cual el health
  check del Outbox pasa a `DEGRADED`; 1000 por defecto)

### Alta de tenants y administración de plataforma (ADR-0010)

- `PLATFORM_ADMIN_EMAIL` / `PLATFORM_ADMIN_PASSWORD`: credenciales del
  `PLATFORM_ADMIN` inicial, creado al arrancar si ambas están definidas. Si se
  dejan vacías **no se crea ninguno**, y entonces nadie puede dar de alta
  tenants desde `/platform`. La contraseña es un secreto: va en el gestor de
  secretos del entorno, nunca en un `.env` versionado.
  - Rotación: cambia `PLATFORM_ADMIN_PASSWORD` y reinicia el backend. El arranque
    no reescribe la contraseña de un `PLATFORM_ADMIN` ya existente; para
    cambiarla, usa el flujo de cambio de contraseña de la aplicación.
- `PUBLIC_REGISTRATION_ENABLED`: habilita el registro público autoservicio
  (`POST /api/v1/public/tenant-registrations`, RF-TEN-010). **Habilitado por
  defecto**: cualquiera puede enviar una solicitud de alta, pero esta solo crea
  una `TenantRegistration` pendiente — el tenant no existe hasta que su correo
  queda verificado y un `PLATFORM_ADMIN` aprueba la solicitud desde
  `/platform/registrations` (T53-03).
  - Con `false`, el endpoint responde 403 y **`scripts/smoke.sh` y
    `scripts/seed-demo.sh` fallan**, porque ambos registran un tenant.
  - Si el producto no debe ofrecer autoservicio todavía (p. ej. antes de un
    lanzamiento anunciado), fija `PUBLIC_REGISTRATION_ENABLED=false` por
    entorno: el alta de tenants queda entonces como una operación exclusiva de
    `PLATFORM_ADMIN`.

### Backups (T150, ADR-0013)

- `BACKUP_DIR` (por defecto `./backups`), `BACKUP_RETENTION_DAYS` (7),
  `BACKUP_RETENTION_WEEKLY` (4)
- `BACKUP_PASSPHRASE`: passphrase GPG del cifrado de los volcados. Se custodia
  en el gestor de secretos, **no** junto a los backups. Si se pierde, todos los
  backups cifrados son irrecuperables.

## Backups y restauración PostgreSQL

Procedimiento completo, política de retención y evidencia de los simulacros:
[`docs/manuals/backup-restore.md`](backup-restore.md). Estrategia:
[ADR-0013](../adr/ADR-0013-estrategia-backup-retencion.md).

Resumen operativo:

- Backup (diario por cron, o manual):
  `BACKUP_PASSPHRASE=... scripts/backup/backup-postgres.sh`
  Genera `backups/timetracking-<db>-<ts>.dump.gpg` cifrado con AES-256, su
  `.sha256` y un log; rota según la retención y devuelve código de salida
  significativo (0 correcto, 1 fallo, 2 configuración).
- Restauración (**destructiva**: recrea la base):
  `BACKUP_PASSPHRASE=... scripts/backup/restore-postgres.sh <fichero.dump.gpg>`
  Detiene la aplicación, restaura, valida esquema y migraciones Flyway, y
  arranca de nuevo. Después: `bash scripts/smoke.sh`.
- Simulacro de restauración: obligatorio una vez por versión relevante y, como
  mínimo, trimestral. Último realizado: 2026-08-04, correcto en 13 s.

## Escaneo de seguridad en CI

Política de severidad, excepciones y aprobaciones:
[`docs/security/dependency-scanning-policy.md`](../security/dependency-scanning-policy.md).

- Secretos: `gitleaks git . --config .gitleaks.toml --redact` (cualquier
  hallazgo rompe el build).
- Dependencias frontend: `scripts/security/npm-audit-gate.sh`.
- Dependencias backend: requiere el secreto `NVD_API_KEY` en GitHub Actions.

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
