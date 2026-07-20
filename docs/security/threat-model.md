# Modelo de amenazas final

## Alcance

MVP multitenant de control horario con autenticación JWT, refresh token por
cookie, auditoría y Transactional Outbox.

## Superficies principales

- API pública de autenticación (`register`, `login`, `refresh`).
- API autenticada de negocio (`employees`, `workdays`, `corrections`,
  `reports`, `audit-events`).
- Persistencia PostgreSQL con datos multitenant.
- Publicador de outbox y consumidores idempotentes.
- Frontend Angular servido por nginx con proxy `/api`.

## Amenazas y mitigaciones

| Amenaza | Riesgo | Mitigación actual |
|---|---|---|
| Suplantación | Robo o reutilización de credenciales/tokens | BCrypt, rate limiting, JWT firmado, refresh token rotatorio y revocación por reutilización |
| Fuga entre tenants | Acceso cruzado o uso de `tenantId` forjado | `TenantContext` derivado del JWT, consultas tenant-aware, tests cross-tenant |
| Enumeración de usuarios | Descubrir emails válidos mediante `register` | Mensaje uniforme sin reflejar el correo existente |
| Errores verbosos | Exposición de stack trace o SQL | `GlobalExceptionHandler` con 500 genérico |
| Sobrecarga por payloads grandes | Consumo excesivo de memoria o logs | Límite backend `RequestSizeLimitFilter` y `client_max_body_size` en nginx |
| Misconfiguración web | Clickjacking, sniffing, CORS abierto | `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, CORS restringido |
| Pérdida de eventos | Cambio de negocio sin evento o caída durante publicación | Outbox persistido en la misma transacción, reintentos con backoff |
| Duplicación de eventos | Redelivery at-least-once | Consumidores idempotentes por `eventId` |
| Repudio | Negar aprobación/rechazo de correcciones | Auditoría append-only con actor, tenant y correlationId |

## Riesgos residuales aceptados

- Sin WAF ni rate limit distribuido: el limitador actual es en memoria y vale
  para el MVP / demo, no para escala horizontal sin coordinación.
- Sin escáner automatizado de vulnerabilidades dedicado en CI: mitigado por
  versiones fijadas y revisión manual, pendiente de industrialización futura.
- Sin broker externo ni dead-letter queue: el estado `FAILED` del outbox se
  gestiona operativamente mediante reintento manual.
