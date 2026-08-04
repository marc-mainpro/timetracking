# Manual de operación

## Arranque y parada

- Local/demo: `cp .env.example .env && docker compose up -d --build`
- Parada: `docker compose down`
- Limpieza completa: `docker compose down -v`

## Variables de entorno clave

### Base de datos y aplicación

- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `JWT_SECRET`
- `FRONTEND_ORIGIN`
- `AUTH_REFRESH_COOKIE_SECURE`
- `APP_REQUEST_MAX_PAYLOAD_BYTES`

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
  (`POST /api/v1/auth/register`, RF-TEN-010). **Deshabilitado por defecto**: el
  alta de tenants es una operación de `PLATFORM_ADMIN`.
  - Con `false`, el endpoint responde 404/403 y **`scripts/smoke.sh` y
    `scripts/seed-demo.sh` fallan**, porque ambos registran un tenant. Ponlo a
    `true` antes de ejecutarlos.
  - En una instalación real déjalo a `false` salvo que el producto ofrezca
    explícitamente autoservicio: con `true`, cualquiera con acceso a la API
    puede crear tenants.

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

## Métricas

- Health: `/actuator/health`
- Métricas: `/actuator/metrics`
- Relevantes: `outbox.messages.published`, `outbox.messages.retried`,
  `outbox.messages.failed`, `outbox.publish.duration`.

## Problemas comunes

- Login no refresca en local: revisar `AUTH_REFRESH_COOKIE_SECURE=false`.
- Frontend no alcanza la API: comprobar que `frontend` esté healthy y que nginx
  haga proxy a `backend:8080`.
- 413 en peticiones grandes: revisar `APP_REQUEST_MAX_PAYLOAD_BYTES` y
  `client_max_body_size` de nginx.
