# Diccionario de datos

Detalle de las 26 tablas del esquema, agrupadas por módulo. Para el mapa de
relaciones, ver [relaciones.md](relaciones.md); para el orden en que se
construyó, [migraciones.md](migraciones.md).

En las tablas de columnas, **N** indica si admite `NULL` (`sí`/`no`). Todos los
`TIMESTAMPTZ` se guardan en UTC. `flyway_schema_history`, gestionada por Flyway,
no se documenta aquí.

## Índice

| Módulo | Tablas |
| --- | --- |
| [Identidad y acceso](#identidad-y-acceso) | `tenant`, `app_user`, `user_role`, `user_session`, `refresh_token`, `password_reset_token`, `account_lockout` |
| [Alta de organizaciones](#alta-de-organizaciones) | `tenant_registration` |
| [Control horario](#control-horario) | `workday`, `break_entry`, `hourly_rules`, `workday_evaluation` |
| [Correcciones](#correcciones) | `correction_request` |
| [Calendarios](#calendarios) | `work_calendar`, `calendar_day_rule`, `calendar_holiday`, `calendar_special_day`, `calendar_assignment` |
| [Turnos](#turnos) | `shift_template`, `shift_assignment` |
| [Ausencias](#ausencias) | `absence_type`, `absence_request` |
| [Notificaciones](#notificaciones) | `notification` |
| [Mensajería y auditoría](#mensajería-y-auditoría) | `outbox_message`, `processed_event`, `audit_event` |

---

## Identidad y acceso

### `tenant`

Organización cliente. Raíz de todo el aislamiento de datos.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `name` | `VARCHAR(200)` | no | Nombre comercial |
| `status` | `VARCHAR(20)` | no | `PENDING`, `ACTIVE`, `SUSPENDED`, `ARCHIVED` |
| `timezone` | `VARCHAR(60)` | no | Zona IANA por defecto de la organización |
| `activated_at` | `TIMESTAMPTZ` | sí | Primera activación |
| `suspended_at` | `TIMESTAMPTZ` | sí | Última suspensión |
| `archived_at` | `TIMESTAMPTZ` | sí | Archivado (estado terminal) |
| `suspension_reason` | `VARCHAR(500)` | sí | Motivo de la suspensión vigente |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | no | Auditoría técnica |

- `ck_tenant_status`: el estado pertenece al conjunto de cuatro valores.
- `ix_tenant_status_created_at (status, created_at DESC)`: listado de plataforma
  filtrado por estado.
- Fila fija `00000000-0000-0000-0000-000000000001` ("Plataforma"): tenant de
  sistema al que pertenecen los `PLATFORM_ADMIN`; se excluye de los listados.

### `app_user`

Usuario de la aplicación, siempre perteneciente a un tenant.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `tenant_id` | `UUID` | no | FK → `tenant.id` |
| `email` | `VARCHAR(320)` | no | **Único globalmente** (`uq_app_user_email`) |
| `password_hash` | `VARCHAR(100)` | no | BCrypt; nunca la contraseña |
| `first_name` / `last_name` | `VARCHAR(100)` | no | Nombre y apellidos |
| `status` | `VARCHAR(20)` | no | `ACTIVE`, `INACTIVE` |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | no | Auditoría técnica |

- `ix_app_user_tenant_id (tenant_id)`: listado de empleados del tenant.
- La baja de un empleado es **lógica** (`status = 'INACTIVE'`): sus jornadas,
  ausencias y correcciones siguen siendo consultables.

### `user_role`

Roles de un usuario. Tabla y no columna: un usuario puede acumular varios.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `user_id` | `UUID` | no | PK, FK → `app_user.id` |
| `role` | `VARCHAR(50)` | no | PK. `PLATFORM_ADMIN`, `TENANT_ADMIN`, `EMPLOYEE` |

`PLATFORM_ADMIN` no es asignable desde dentro de un tenant ni por registro
público: se aprovisiona en el arranque desde variables de entorno.

### `user_session`

Sesión de navegador, unidad revocable de acceso.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `user_id` | `UUID` | no | FK → `app_user.id` |
| `tenant_id` | `UUID` | no | FK → `tenant.id` |
| `created_at` | `TIMESTAMPTZ` | no | Inicio de sesión |
| `last_used_at` | `TIMESTAMPTZ` | no | Último refresco |
| `expires_at` | `TIMESTAMPTZ` | no | Caducidad absoluta |
| `revoked_at` | `TIMESTAMPTZ` | sí | `NULL` = sesión viva |
| `user_agent_hash` | `VARCHAR(64)` | sí | SHA-256 del `User-Agent` |
| `ip_hash` | `VARCHAR(64)` | sí | SHA-256 de la IP de origen |

- `ix_user_session_active (tenant_id, user_id, revoked_at, expires_at)`:
  sesiones vivas del usuario, que es la consulta del panel "mis sesiones".

### `refresh_token`

Token de refresco con rotación. Cada uso emite uno nuevo y marca el anterior.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `user_id` | `UUID` | no | FK → `app_user.id` |
| `session_id` | `UUID` | sí | FK → `user_session.id`. Nullable por compatibilidad con los tokens anteriores a `V17` |
| `token_hash` | `VARCHAR(64)` | no | SHA-256 del token, **único** |
| `expires_at` | `TIMESTAMPTZ` | no | Caducidad |
| `revoked_at` | `TIMESTAMPTZ` | sí | Revocación explícita |
| `replaced_by` | `UUID` | sí | Token que lo sustituyó en la rotación |
| `created_at` | `TIMESTAMPTZ` | no | Emisión |

Reutilizar un token ya rotado es la señal de robo: `replaced_by` permite
detectarla y revocar toda la cadena
([ADR-0004](../adr/ADR-0004-jwt-refresh-rotatorio-cookie-httponly.md)).

### `password_reset_token`

Token de un solo uso para restablecer la contraseña.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `tenant_id` | `UUID` | no | FK → `tenant.id` |
| `user_id` | `UUID` | no | FK → `app_user.id`, `ON DELETE CASCADE` |
| `token_hash` | `VARCHAR(64)` | no | SHA-256, **único** |
| `expires_at` | `TIMESTAMPTZ` | no | Caducidad |
| `used_at` | `TIMESTAMPTZ` | sí | `NOT NULL` = ya consumido |
| `created_at` | `TIMESTAMPTZ` | no | Emisión |

- `ix_password_reset_token_user_created_at (user_id, created_at DESC)`: sirve
  también para limitar la frecuencia de solicitudes por usuario.

### `account_lockout`

Contador de intentos fallidos y bloqueo temporal. **Una fila por usuario**: la
clave primaria es `user_id`.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `user_id` | `UUID` | no | PK, FK → `app_user.id`, `ON DELETE CASCADE` |
| `tenant_id` | `UUID` | no | FK → `tenant.id`. Redundante a propósito |
| `failed_attempts` | `INTEGER` | no | ≥ 0, por defecto 0 |
| `last_failed_attempt_at` | `TIMESTAMPTZ` | sí | Último intento fallido |
| `locked_until` | `TIMESTAMPTZ` | sí | Fin del bloqueo; `NULL` = no bloqueada |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | no | Auditoría técnica |

---

## Alta de organizaciones

### `tenant_registration`

Solicitud de alta pública. Independiente de `tenant`: existe antes que él y
puede no convertirse nunca en organización.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `company_name` | `VARCHAR(200)` | no | Nombre propuesto |
| `owner_first_name` / `owner_last_name` | `VARCHAR(200)` | no | Datos del futuro administrador |
| `email` | `VARCHAR(255)` | no | Correo a verificar |
| `owner_password_hash` | `VARCHAR(255)` | no | BCrypt, antes de existir el usuario |
| `timezone` | `VARCHAR(60)` | no | Zona IANA propuesta |
| `status` | `VARCHAR(30)` | no | `PENDING_EMAIL_VERIFICATION`, `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `EXPIRED`, `CONSUMED` |
| `verification_token_hash` | `VARCHAR(64)` | sí | SHA-256; se borra al consumirse |
| `verification_token_expires_at` | `TIMESTAMPTZ` | sí | Caducidad del enlace |
| `verification_sent_at` | `TIMESTAMPTZ` | sí | Último envío |
| `resend_count` | `INTEGER` | no | ≥ 0. Reenvíos del correo |
| `source` | `VARCHAR(40)` | no | Origen de la solicitud |
| `ip_hash` | `VARCHAR(64)` | sí | SHA-256 de la IP; límite por IP |
| `decision_reason` | `VARCHAR(500)` | sí | Motivo de aprobación o rechazo |
| `created_tenant_id` | `UUID` | sí | FK → `tenant.id`, solo si `CONSUMED` |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | no | Auditoría técnica |
| `verified_at` / `decided_at` | `TIMESTAMPTZ` | sí | Hitos del flujo |

- `ck_tenant_registration_consumed_has_tenant`: `CONSUMED` ⟺ hay tenant creado.
- `ux_tenant_registration_verification_token_hash … WHERE … IS NOT NULL`: un
  token vivo identifica como mucho una solicitud. Es parcial porque los `NULL`
  no deben colisionar entre sí.
- Índices por `email`, `status` e `ip_hash`, todos con `created_at DESC`:
  búsqueda de la solicitud viva, listado de revisión y límites por correo/IP.

---

## Control horario

### `workday`

Jornada de un empleado. Agregado raíz del módulo.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `tenant_id` | `UUID` | no | Sin FK (tabla de alto volumen) |
| `employee_id` | `UUID` | no | FK → `app_user.id` |
| `status` | `VARCHAR(20)` | no | `OPEN`, `ON_BREAK`, `CLOSED`, `ADJUSTED` |
| `started_at` | `TIMESTAMPTZ` | no | Inicio |
| `ended_at` | `TIMESTAMPTZ` | sí | Cierre; `NULL` mientras está abierta |
| `version` | `BIGINT` | no | Bloqueo optimista |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | no | Auditoría técnica |

- `ux_workday_active (tenant_id, employee_id) WHERE status IN ('OPEN','ON_BREAK')`:
  **como mucho una jornada activa por empleado**, garantizado por la base de datos.
- `ix_workday_tenant_employee_started_at`: histórico e informes por rango.

### `break_entry`

Pausa dentro de una jornada. No tiene vida propia: se borra con ella.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `workday_id` | `UUID` | no | FK → `workday.id`, `ON DELETE CASCADE` |
| `started_at` | `TIMESTAMPTZ` | no | Inicio de la pausa |
| `ended_at` | `TIMESTAMPTZ` | sí | `NULL` = pausa en curso |

- `ux_break_entry_open (workday_id) WHERE ended_at IS NULL`: una sola pausa
  abierta por jornada.
- `ix_break_entry_workday_id`: añadido en `V25` para los informes, que leen las
  pausas **cerradas** y por tanto no podían usar el índice parcial anterior.

### `hourly_rules`

Reglas horarias del tenant. Una fila por organización.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `tenant_id` | `UUID` | no | PK, FK → `tenant.id` |
| `max_daily_work_minutes` | `INTEGER` | sí | Jornada máxima; `> 0` si se define |
| `required_break_minutes` | `INTEGER` | sí | Descanso obligatorio; `>= 0` |
| `rounding_step_minutes` | `INTEGER` | sí | Paso de redondeo; `> 0` |
| `tolerance_minutes` | `INTEGER` | sí | Tolerancia de desviación; `>= 0` |

Todas las reglas son nullable: `NULL` significa "no se aplica esta regla",
distinto de cero.

### `workday_evaluation`

Resultado de evaluar una jornada al cerrarla. **Uno a uno** con `workday`.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `workday_id` | `UUID` | no | PK, FK → `workday.id`, `ON DELETE CASCADE` |
| `tenant_id` | `UUID` | no | FK → `tenant.id` |
| `employee_id` | `UUID` | no | Copia para informes sin `JOIN` |
| `expected_minutes` | `BIGINT` | no | Tiempo previsto resuelto (ausencia → turno → calendario) |
| `worked_minutes` | `BIGINT` | no | Tiempo real trabajado |
| `effective_worked_minutes` | `BIGINT` | no | Trabajado tras aplicar el redondeo |
| `paused_minutes` | `BIGINT` | no | Total de pausas |
| `overtime_minutes` | `BIGINT` | no | Exceso sobre lo previsto |
| `deviation_minutes` | `BIGINT` | no | Desviación absoluta frente a lo previsto |
| `anomalies` | `VARCHAR(300)` | no | Nombres de `WorkdayAnomaly` separados por comas y ordenados; cadena vacía = sin anomalías |
| `evaluated_at` | `TIMESTAMPTZ` | no | Momento de la evaluación |

Anomalías posibles: `MAX_DAILY_WORK_EXCEEDED`, `REQUIRED_BREAK_NOT_MET`. Todos
los minutos tienen `CHECK … >= 0`. La fila es una **foto**: cambiar las reglas
del tenant no recalcula las evaluaciones ya hechas.

---

## Correcciones

### `correction_request`

Solicitud de corrección de una jornada ya cerrada.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `tenant_id` | `UUID` | no | Sin FK |
| `workday_id` | `UUID` | no | FK → `workday.id` |
| `requested_by` | `UUID` | no | FK → `app_user.id` |
| `reason` | `TEXT` | no | Justificación del empleado |
| `proposed_changes` | `JSONB` | no | Campos propuestos; se lee entero, no se consulta por contenido |
| `status` | `VARCHAR(20)` | no | `PENDING`, `APPROVED`, `REJECTED` |
| `resolved_by` | `UUID` | sí | FK → `app_user.id` |
| `resolved_at` | `TIMESTAMPTZ` | sí | Momento de la resolución |
| `resolution_comment` | `TEXT` | sí | Comentario del responsable |
| `version` | `BIGINT` | no | Bloqueo optimista |
| `created_at` | `TIMESTAMPTZ` | no | Alta de la solicitud |

- `ux_correction_request_pending (workday_id, requested_by) WHERE status = 'PENDING'`:
  una sola solicitud viva por empleado y jornada.
- `ix_correction_request_tenant_created_at`: listado del tenant, añadido en
  `V25` tras medir que recorría las correcciones de todos los demás.

Aprobar una corrección lleva la jornada a `ADJUSTED`.

---

## Calendarios

### `work_calendar`

Calendario laboral. Agregado raíz que contiene reglas, festivos y jornadas
especiales.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `tenant_id` | `UUID` | no | FK → `tenant.id` |
| `name` | `VARCHAR(120)` | no | Único por tenant (`ux_work_calendar_tenant_name`) |
| `timezone` | `VARCHAR(60)` | no | Zona IANA; convierte fecha local ↔ instante |
| `valid_from` | `DATE` | no | Inicio de vigencia |
| `valid_to` | `DATE` | sí | Fin; `NULL` = sin fin. `CHECK valid_to >= valid_from` |
| `status` | `VARCHAR(20)` | no | `ACTIVE`, `ARCHIVED` (borrado lógico) |
| `version` | `BIGINT` | no | Bloqueo optimista |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | no | Auditoría técnica |

### `calendar_day_rule`

Regla semanal. Un día sin fila es no laborable.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `calendar_id` | `UUID` | no | PK, FK → `work_calendar.id`, `ON DELETE CASCADE` |
| `day_of_week` | `VARCHAR(10)` | no | PK. `MONDAY`…`SUNDAY` |
| `working` | `BOOLEAN` | no | Si el día es laborable |
| `expected_minutes` | `INTEGER` | no | Entre 0 y 1440 |

`ck_calendar_day_rule_coherent` impide la combinación incoherente: laborable con
cero minutos, o no laborable con jornada esperada.

### `calendar_holiday`

Festivo. Siempre no laborable.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `calendar_id` | `UUID` | no | PK, FK → `work_calendar.id`, `ON DELETE CASCADE` |
| `holiday_date` | `DATE` | no | PK. Un festivo por fecha y calendario |
| `name` | `VARCHAR(120)` | no | Denominación |

### `calendar_special_day`

Jornada especial que **sustituye** la esperada de una fecha concreta.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `calendar_id` | `UUID` | no | PK, FK → `work_calendar.id`, `ON DELETE CASCADE` |
| `special_date` | `DATE` | no | PK |
| `name` | `VARCHAR(120)` | no | Denominación |
| `expected_minutes` | `INTEGER` | no | Entre 0 y 1440; `0` la deja no laborable |

### `calendar_assignment`

Asignación de un calendario a un ámbito.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `tenant_id` | `UUID` | no | FK → `tenant.id` |
| `calendar_id` | `UUID` | no | FK → `work_calendar.id`, `ON DELETE CASCADE` |
| `scope` | `VARCHAR(20)` | no | `TENANT`, `TEAM`, `EMPLOYEE` |
| `scope_target_id` | `UUID` | sí | Identificador **opaco**, sin FK. `NULL` solo en ámbito `TENANT` |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | no | Auditoría técnica |

`ck_calendar_assignment_target` impone la correspondencia entre ámbito y
destinatario. Los dos índices únicos parciales que garantizan una asignación por
ámbito están explicados en [relaciones.md](relaciones.md#planificación-calendarios-y-turnos).

---

## Turnos

### `shift_template`

Plantilla de turno reutilizable.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `tenant_id` | `UUID` | no | FK → `tenant.id` |
| `name` | `VARCHAR(120)` | no | Único por tenant |
| `start_time` | `TIME` | no | Hora de entrada |
| `end_time` | `TIME` | no | Hora de salida; si es menor que la de entrada, el turno **cruza medianoche** |
| `planned_break_minutes` | `INTEGER` | no | ≥ 0 |
| `status` | `VARCHAR(20)` | no | `ACTIVE`, `ARCHIVED` |

`ck_shift_template_non_zero_duration` solo prohíbe `start_time = end_time`.

### `shift_assignment`

Asignación de un turno a un empleado durante un periodo.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `tenant_id` | `UUID` | no | FK → `tenant.id` |
| `employee_id` | `UUID` | no | FK → `app_user.id` |
| `shift_template_id` | `UUID` | no | FK → `shift_template.id` |
| `valid_from` | `DATE` | no | Inicio de vigencia |
| `valid_to` | `DATE` | sí | Fin; `NULL` = sin fin. `CHECK valid_to >= valid_from` |

- `ix_shift_assignment_tenant_employee (tenant_id, employee_id, valid_from, valid_to)`:
  resolución del turno vigente en una fecha.

El turno asignado **prevalece sobre el calendario** como tiempo previsto de la
jornada.

---

## Ausencias

### `absence_type`

Catálogo de tipos de ausencia, propio de cada tenant.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `tenant_id` | `UUID` | no | FK → `tenant.id` |
| `code` | `VARCHAR(40)` | no | Único por tenant (`uq_absence_type_tenant_code`) |
| `name` | `VARCHAR(120)` | no | Denominación visible |
| `requires_approval` | `BOOLEAN` | no | Si necesita resolución de un responsable |
| `allows_attachment` | `BOOLEAN` | no | Si admite justificante |
| `active` | `BOOLEAN` | no | Baja lógica del tipo |

### `absence_request`

Solicitud de ausencia de un empleado sobre un rango de fechas.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `tenant_id` | `UUID` | no | FK → `tenant.id` |
| `employee_id` | `UUID` | no | FK → `app_user.id` |
| `absence_type_id` | `UUID` | no | FK → `absence_type.id` |
| `start_date` / `end_date` | `DATE` | no | Rango inclusivo. `CHECK end_date >= start_date` |
| `reason` | `VARCHAR(500)` | sí | Motivo del empleado |
| `status` | `VARCHAR(20)` | no | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` |
| `resolved_by` | `UUID` | sí | FK → `app_user.id` |
| `resolved_at` | `TIMESTAMPTZ` | sí | Momento de la resolución |
| `resolution_comment` | `VARCHAR(500)` | sí | Comentario del responsable |
| `created_at` | `TIMESTAMPTZ` | no | Alta de la solicitud |

- `ix_absence_request_tenant_employee_date` y `ix_absence_request_tenant_date`:
  detección de solapes y calendario de ausencias del tenant.

Una ausencia aprobada que cubre la fecha de la jornada fija su tiempo previsto,
por delante del turno y del calendario.

---

## Notificaciones

### `notification`

Aviso dirigido a un usuario. Reúne el aviso en la aplicación y su entrega por
correo en una sola fila.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `tenant_id` | `UUID` | no | Sin FK |
| `recipient_user_id` | `UUID` | no | Sin FK |
| `recipient_email` | `VARCHAR(320)` | sí | Desnormalizado: foto del momento del hecho |
| `type` | `VARCHAR(50)` | no | Tipo de aviso (ver más abajo) |
| `title` | `VARCHAR(200)` | no | Título |
| `body` | `VARCHAR(2000)` | no | Cuerpo |
| `status` | `VARCHAR(20)` | no | `PENDING`, `SENT`, `FAILED`, `CANCELLED`, `DISCARDED` |
| `email_required` | `BOOLEAN` | no | Si además debe enviarse por correo |
| `action_path` | `VARCHAR(200)` | sí | Ruta del frontend a la que lleva; `NULL` si es informativa |
| `attempts` | `INTEGER` | no | Intentos de envío |
| `last_error` | `VARCHAR(500)` | sí | Último error de entrega |
| `created_at` | `TIMESTAMPTZ` | no | Emisión |
| `sent_at` | `TIMESTAMPTZ` | sí | Entrega efectiva |
| `read_at` | `TIMESTAMPTZ` | sí | `NULL` = no leída; alimenta el contador |

Tipos: `WORKDAY_ANOMALY_DETECTED`, `TEAM_WORKDAY_ANOMALY`,
`CORRECTION_REQUESTED`, `CORRECTION_APPROVED`, `CORRECTION_REJECTED`,
`ABSENCE_REQUESTED`, `ABSENCE_APPROVED`, `ABSENCE_REJECTED`, `SHIFT_ASSIGNED`,
`ACCOUNT_CREATED`, `ACCOUNT_DEACTIVATED`, `TENANT_SUSPENDED`,
`TENANT_REACTIVATED`, `TENANT_ARCHIVED`, `REGISTRATION_PENDING_REVIEW`,
`SYSTEM_QUEUE_STUCK`.

Índices, los tres pensados para una consulta concreta:

| Índice | Para |
| --- | --- |
| `ix_notification_tenant_recipient_created_at` | Buzón del usuario |
| `ix_notification_pending (created_at) WHERE status = 'PENDING' AND email_required` | Cola de envío; parcial porque las pendientes son minoría |
| `ix_notification_failed (created_at) WHERE status = 'FAILED'` | Panel de plataforma, por antigüedad |

---

## Mensajería y auditoría

### `outbox_message`

Cola de eventos de integración escrita en la misma transacción que el cambio de
negocio ([ADR-0005](../adr/ADR-0005-transactional-outbox-sin-broker.md)).

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK. Es el `eventId` del envelope publicado |
| `tenant_id` | `UUID` | sí | `NULL` en hechos de plataforma |
| `aggregate_type` | `VARCHAR(100)` | no | Tipo del agregado origen |
| `aggregate_id` | `UUID` | no | Id del agregado; sin FK, apunta a varias tablas |
| `event_type` | `VARCHAR(200)` | no | Nombre del evento |
| `event_version` | `INTEGER` | no | Versión del contrato del evento |
| `payload` | `JSONB` | no | Cuerpo del evento |
| `occurred_at` | `TIMESTAMPTZ` | no | Momento del hecho |
| `published_at` | `TIMESTAMPTZ` | sí | Publicación efectiva |
| `attempts` | `INTEGER` | no | Intentos de publicación |
| `next_attempt_at` | `TIMESTAMPTZ` | sí | Reintento con retroceso exponencial |
| `last_error` | `TEXT` | sí | Último error |
| `status` | `VARCHAR(30)` | no | `PENDING`, `PROCESSING`, `PUBLISHED`, `FAILED`, `DISCARDED` |
| `created_at` | `TIMESTAMPTZ` | no | Alta de la fila |

A diferencia de `notification`, el estado **no** tiene `CHECK`: se valida solo
en el enumerado del dominio.

- `ix_outbox_message_status_next_attempt_at`: reclamación del publicador.
- `ix_outbox_message_failed (created_at) WHERE status = 'FAILED'`: panel de
  colas fallidas.

Catálogo de eventos en
[`docs/integration/event-catalog.md`](../integration/event-catalog.md).

### `processed_event`

Deduplicación de eventos por consumidor. Hace idempotente la entrega
*at-least-once*.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `event_id` | `UUID` | no | PK. Igual a `outbox_message.id` |
| `consumer` | `VARCHAR(200)` | no | PK. Identificador del consumidor |
| `processed_at` | `TIMESTAMPTZ` | no | Momento del procesado |

La clave es compuesta desde `V23`. Con la clave anterior (solo `event_id`), el
primer consumidor que marcaba el evento dejaba a los demás creyendo que ya
estaba procesado.

### `audit_event`

Registro de auditoría, de solo escritura.

| Columna | Tipo | N | Descripción |
| --- | --- | --- | --- |
| `id` | `UUID` | no | PK |
| `tenant_id` | `UUID` | sí | `NULL` en acciones de plataforma |
| `actor_user_id` | `UUID` | sí | `NULL` cuando actúa el sistema |
| `action` | `VARCHAR(100)` | no | Acción registrada |
| `entity_type` | `VARCHAR(100)` | no | Tipo de entidad afectada |
| `entity_id` | `UUID` | sí | Id de la entidad; sin FK |
| `correlation_id` | `UUID` | no | Correlación de toda la petición |
| `metadata` | `JSONB` | no | Contexto adicional del hecho |
| `occurred_at` | `TIMESTAMPTZ` | no | Momento del hecho |

- `ix_audit_event_tenant_occurred_at` y `ix_audit_event_tenant_action_occurred_at`:
  consulta de auditoría del tenant, con y sin filtro de acción.
- `ix_audit_event_correlation_id`: reconstruir todo lo ocurrido en una petición.
