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
