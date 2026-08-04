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

## Fuerza bruta sobre autenticación (T30-03 / T30-04, RS-007, RS-008)

Sección añadida con el bloqueo temporal de cuentas y la ampliación del rate
limiting. Ver ADR-0013 para las decisiones de diseño.

### Dos controles, dos ataques distintos

| Ataque | Control que lo frena | Dónde vive el estado |
|---|---|---|
| Ráfaga de intentos desde una IP | Rate limiting por IP y endpoint (bucket4j) | Memoria del proceso |
| Diccionario lento contra una cuenta, distribuido entre muchas IPs | Bloqueo temporal de cuenta (5 intentos / 15 min) | Tabla `account_lockout` |
| Password spraying (una contraseña común contra muchas cuentas) | Ninguno de los dos por completo | — (ver riesgos residuales) |

Son complementarios a propósito: el rate limiting no ve el ataque distribuido y
el bloqueo por cuenta no ve la ráfaga contra cuentas distintas.

### Amenazas cubiertas

| Amenaza | Riesgo | Mitigación |
|---|---|---|
| Fuerza bruta de contraseñas | Compromiso de cuenta | Bloqueo temporal configurable (`auth.account-lockout.*`) + rate limiting por endpoint |
| Enumeración de usuarios vía bloqueo | Descubrir qué emails existen probando N intentos y observando el cambio de respuesta | La respuesta solo cambia para quien acierta la contraseña; con credenciales incorrectas, cuenta bloqueada, cuenta existente y email inventado devuelven un Problem Details idéntico (comprobado byte a byte en `AccountLockoutIntegrationTest`) |
| Denegación de servicio dirigida | Bloquear la cuenta de un tercero de forma indefinida | El bloqueo es temporal y **no se prolonga** con los intentos posteriores; expira solo |
| Abuso de endpoints que envían correo | Bombardear el buzón de un tercero vía recuperación de contraseña o reenvío de verificación | Regla de rate limiting propia y más estricta (5/15 min) sobre `/api/v1/auth/password/**` y `/api/v1/auth/verification/**`, activa desde antes de que esos endpoints existan |
| Expulsión de usuarios legítimos | Varios empleados tras el mismo NAT agotan la cuota de `refresh` | Límite propio y más alto para `refresh` (30/min) |
| Repudio de un incidente de acceso | Negar los intentos fallidos previos a un compromiso | Auditoría `LOGIN_FAILED`, `ACCOUNT_LOCKED` y `LOGIN_ATTEMPT_WHILE_LOCKED` con tenant, actor y correlationId |
| Punto ciego de detección | Un ataque en curso pasa desapercibido | Métricas `auth.login.failed{reason}` y `auth.accounts.locked` expuestas por Actuator |
| Desactivación silenciosa del control | Perder o vaciar `config/account-lockout.yml` deja los endpoints sin límite | Sin reglas configuradas el filtro cae al mínimo histórico (login y registro), no a «sin límite» |

### Riesgos residuales

- **Rate limiting no distribuido.** Los contadores viven en memoria del proceso:
  con varias réplicas el límite efectivo se multiplica por el número de
  instancias y un reinicio lo reinicia. Aceptado ya en `owasp-review.md`; no se
  resuelve introduciendo Redis porque la V2 no admite dependencias nuevas fuera
  de la Ola 0. Lo que **no** depende de la memoria es el bloqueo por cuenta, que
  está persistido. La solución definitiva es un limitador en el borde (WAF o
  ingress), decisión de despliegue.
- **Password spraying.** Una contraseña común probada una sola vez contra muchas
  cuentas no llega al umbral de ninguna de ellas, y si el atacante rota IPs
  tampoco agota el límite por IP. Mitigado parcialmente por la política de
  contraseñas y por la métrica agregada `auth.login.failed`, que sí se dispara.
  Un límite global por endpoint o una detección de anomalías quedan fuera del
  alcance de esta iteración.
- **Confianza en `X-Forwarded-For`.** El filtro toma la IP del cliente de esa
  cabecera, que un cliente directo puede falsificar para eludir el límite. Es
  correcto solo si el despliegue termina siempre en un proxy que la reescribe
  (nginx en el compose actual lo hace). Un despliegue que exponga el backend
  directamente pierde el control.
- **Volumen de auditoría.** Cada intento fallido escribe una fila en
  `audit_event`, a un ritmo que marca el atacante. Queda acotado por el rate
  limiting y por el propio bloqueo, pero conviene vigilar el crecimiento de la
  tabla en un ataque sostenido.
- **Enumeración por `USER_INACTIVE`.** Preexistente y fuera del alcance de
  T30-04: el login responde `USER_INACTIVE` antes de comprobar la contraseña, lo
  que permite distinguir un usuario desactivado de un email inexistente. El
  bloqueo temporal no introduce esta fuga pero tampoco la corrige; debería
  tratarse junto con el resto del flujo de estados de usuario.
