# Relaciones y diagramas

Mapa del esquema: qué depende de qué y por dónde se une cada módulo con el
siguiente. El detalle de columnas está en el
[diccionario de datos](diccionario-de-datos.md).

Los diagramas se escriben en **Mermaid**, igual que los de
[`docs/procesos/`](../procesos/README.md), y siguen dos convenciones:

- Se dibujan **solo las claves relevantes** de cada tabla, no todas sus
  columnas: el objetivo es entender la relación, no sustituir al diccionario.
- Las relaciones **no declaradas como clave ajena** (`tenant_id` de tablas de
  traza, identificadores opacos) aparecen como línea discontinua `..`, y las
  reales como línea continua `--`.

## Mapa global

Veintiséis tablas de negocio agrupadas en siete bloques. `tenant` es la raíz de
todo: cualquier fila del sistema pertenece a una organización, salvo los hechos
de plataforma.

```mermaid
flowchart TB
    subgraph IDENT["Identidad y acceso"]
        tenant[(tenant)]
        app_user[(app_user)]
        user_role[(user_role)]
        user_session[(user_session)]
        refresh_token[(refresh_token)]
        password_reset_token[(password_reset_token)]
        account_lockout[(account_lockout)]
    end

    subgraph ALTA["Alta de organizaciones"]
        tenant_registration[(tenant_registration)]
    end

    subgraph TIME["Control horario"]
        workday[(workday)]
        break_entry[(break_entry)]
        workday_evaluation[(workday_evaluation)]
        hourly_rules[(hourly_rules)]
    end

    subgraph CORR["Correcciones"]
        correction_request[(correction_request)]
    end

    subgraph PLAN["Planificación"]
        work_calendar[(work_calendar)]
        calendar_day_rule[(calendar_day_rule)]
        calendar_holiday[(calendar_holiday)]
        calendar_special_day[(calendar_special_day)]
        calendar_assignment[(calendar_assignment)]
        shift_template[(shift_template)]
        shift_assignment[(shift_assignment)]
    end

    subgraph AUS["Ausencias"]
        absence_type[(absence_type)]
        absence_request[(absence_request)]
    end

    subgraph TEC["Traza y mensajería"]
        notification[(notification)]
        outbox_message[(outbox_message)]
        processed_event[(processed_event)]
        audit_event[(audit_event)]
    end

    tenant --> app_user
    tenant --> hourly_rules
    tenant --> work_calendar
    tenant --> shift_template
    tenant --> absence_type
    tenant_registration -.->|created_tenant_id| tenant

    app_user --> user_role
    app_user --> user_session
    app_user --> account_lockout
    app_user --> password_reset_token
    user_session --> refresh_token

    app_user -->|employee_id| workday
    workday --> break_entry
    workday --> workday_evaluation
    workday --> correction_request
    hourly_rules -.->|regla aplicada| workday_evaluation

    work_calendar --> calendar_day_rule
    work_calendar --> calendar_holiday
    work_calendar --> calendar_special_day
    work_calendar --> calendar_assignment
    shift_template --> shift_assignment
    app_user -->|employee_id| shift_assignment
    absence_type --> absence_request
    app_user -->|employee_id| absence_request

    calendar_assignment -.->|tiempo previsto| workday_evaluation
    shift_assignment -.->|tiempo previsto| workday_evaluation
    absence_request -.->|tiempo previsto| workday_evaluation

    outbox_message --> processed_event
    outbox_message -.->|consumidores| notification
```

Las flechas discontinuas del bloque de planificación no son claves ajenas: son
**dependencias de cálculo**. Al cerrar una jornada, el evaluador resuelve el
tiempo esperado consultando, por este orden, la ausencia aprobada del día, el
turno asignado y el calendario efectivo; el resultado se congela en
`workday_evaluation.expected_minutes`.

## Identidad y acceso

```mermaid
erDiagram
    tenant ||--o{ app_user : "tenant_id"
    app_user ||--o{ user_role : "user_id"
    app_user ||--o{ user_session : "user_id"
    app_user ||--o{ refresh_token : "user_id"
    app_user ||--o| account_lockout : "user_id (PK)"
    app_user ||--o{ password_reset_token : "user_id"
    user_session ||--o{ refresh_token : "session_id"
    tenant ||--o{ user_session : "tenant_id"
    tenant ||--o{ password_reset_token : "tenant_id"
    tenant ||--o| account_lockout : "tenant_id"

    tenant {
        uuid id PK
        varchar name
        varchar status "PENDING|ACTIVE|SUSPENDED|ARCHIVED"
        varchar timezone
    }
    app_user {
        uuid id PK
        uuid tenant_id FK
        varchar email UK "único GLOBAL, no por tenant"
        varchar password_hash
        varchar status "ACTIVE|INACTIVE"
    }
    user_role {
        uuid user_id PK,FK
        varchar role PK "PLATFORM_ADMIN|TENANT_ADMIN|EMPLOYEE"
    }
    user_session {
        uuid id PK
        uuid user_id FK
        uuid tenant_id FK
        timestamptz revoked_at "NULL = viva"
        varchar ip_hash
        varchar user_agent_hash
    }
    refresh_token {
        uuid id PK
        uuid user_id FK
        uuid session_id FK
        varchar token_hash UK "SHA-256"
        uuid replaced_by "rotación"
    }
    account_lockout {
        uuid user_id PK,FK
        uuid tenant_id FK
        int failed_attempts
        timestamptz locked_until
    }
    password_reset_token {
        uuid id PK
        uuid user_id FK
        uuid tenant_id FK
        varchar token_hash UK "SHA-256"
        timestamptz used_at "un solo uso"
    }
```

Cuatro relaciones merecen explicación:

- **`app_user.email` es único globalmente**, no por tenant. Es lo que permite
  autenticarse con `email + password` sin pedir la organización
  ([ADR-0008](../adr/ADR-0008-email-global-unico-para-autenticacion.md)). El
  `UNIQUE (tenant_id, email)` original se sustituyó en `V3`.
- **Los roles son una tabla, no una columna.** `user_role` tiene clave primaria
  compuesta `(user_id, role)`: un usuario puede acumular roles y añadir uno no
  reescribe la fila del usuario.
- **`refresh_token` cuelga de `user_session`**, no solo del usuario. La sesión
  es la unidad revocable: revocarla invalida toda la cadena de rotación de
  tokens que nació en ella. `session_id` es nullable porque la columna se
  añadió en `V17`, cuando ya existían tokens sin sesión.
- **`account_lockout` es una tabla aparte y no columnas en `app_user`**
  ([ADR-0014](../adr/ADR-0014-bloqueo-de-cuenta-y-rate-limiting-por-patron.md)):
  el contador se escribe en cada intento fallido, un camino que controla el
  atacante. Mantenerlo fuera evita competir por el bloqueo optimista del
  usuario en cada intento.

## Alta de organizaciones

```mermaid
erDiagram
    tenant_registration |o--o| tenant : "created_tenant_id (solo si CONSUMED)"

    tenant_registration {
        uuid id PK
        varchar company_name
        varchar email
        varchar owner_password_hash
        varchar status "PENDING_EMAIL_VERIFICATION|PENDING_REVIEW|APPROVED|REJECTED|EXPIRED|CONSUMED"
        varchar verification_token_hash UK "SHA-256, se borra al consumir"
        varchar ip_hash
        uuid created_tenant_id FK
    }
    tenant {
        uuid id PK
        varchar status "nace PENDING"
    }
```

La tabla es **deliberadamente independiente de `tenant`**
([ADR-0016](../adr/ADR-0016-solicitud-de-alta-separada-del-tenant.md)): una
solicitud existe antes de que exista el tenant y puede no llegar a existir nunca
(rechazada o caducada). Por eso no tiene `tenant_id` sino `created_tenant_id`,
que solo se rellena al consumirse. Un `CHECK` impone la equivalencia exacta:
`status = 'CONSUMED'` ⟺ `created_tenant_id IS NOT NULL`.

## Control horario

```mermaid
erDiagram
    app_user ||--o{ workday : "employee_id"
    workday ||--o{ break_entry : "workday_id (CASCADE)"
    workday ||--o| workday_evaluation : "workday_id PK (CASCADE)"
    tenant ||--o| hourly_rules : "tenant_id PK"

    workday {
        uuid id PK
        uuid tenant_id "sin FK"
        uuid employee_id FK
        varchar status "OPEN|ON_BREAK|CLOSED|ADJUSTED"
        timestamptz started_at
        timestamptz ended_at
        bigint version "bloqueo optimista"
    }
    break_entry {
        uuid id PK
        uuid workday_id FK
        timestamptz started_at
        timestamptz ended_at "NULL = pausa abierta"
    }
    workday_evaluation {
        uuid workday_id PK,FK
        uuid tenant_id FK
        uuid employee_id
        bigint expected_minutes
        bigint worked_minutes
        bigint effective_worked_minutes "tras redondeo"
        bigint deviation_minutes
        bigint overtime_minutes
        varchar anomalies "lista separada por comas"
    }
    hourly_rules {
        uuid tenant_id PK,FK
        int max_daily_work_minutes
        int required_break_minutes
        int rounding_step_minutes
        int tolerance_minutes
    }
```

Dos invariantes se imponen en la base de datos y no solo en el código, con
**índices únicos parciales**:

- `ux_workday_active` sobre `(tenant_id, employee_id) WHERE status IN ('OPEN',
  'ON_BREAK')`: un empleado no puede tener dos jornadas abiertas a la vez.
  Dos peticiones simultáneas de "iniciar jornada" no dan dos filas: una falla.
- `ux_break_entry_open` sobre `(workday_id) WHERE ended_at IS NULL`: como mucho
  una pausa abierta por jornada.

`workday_evaluation` es **uno-a-uno con la jornada** (su clave primaria *es*
`workday_id`) y se escribe al cerrarla. Guarda el resultado congelado, no una
fórmula: si mañana cambian las `hourly_rules` del tenant, las evaluaciones ya
hechas no se mueven. `hourly_rules` es, a su vez, una fila por tenant —su clave
primaria es `tenant_id`—, con todas las columnas nullable: `NULL` significa
"esa regla no se aplica".

## Correcciones

```mermaid
erDiagram
    workday ||--o{ correction_request : "workday_id"
    app_user ||--o{ correction_request : "requested_by"
    app_user ||--o{ correction_request : "resolved_by"

    correction_request {
        uuid id PK
        uuid tenant_id "sin FK"
        uuid workday_id FK
        uuid requested_by FK
        jsonb proposed_changes
        varchar status "PENDING|APPROVED|REJECTED"
        uuid resolved_by FK
        timestamptz resolved_at
        bigint version
    }
```

`proposed_changes` es `JSONB` porque el conjunto de campos corregibles de una
jornada no es fijo y no se consulta por su contenido: se lee entero al aprobar.
El índice único parcial `ux_correction_request_pending` sobre `(workday_id,
requested_by) WHERE status = 'PENDING'` impide que un empleado acumule dos
solicitudes vivas sobre la misma jornada, sin impedir que vuelva a solicitar
tras un rechazo.

## Planificación: calendarios y turnos

```mermaid
erDiagram
    tenant ||--o{ work_calendar : "tenant_id"
    work_calendar ||--o{ calendar_day_rule : "calendar_id (CASCADE)"
    work_calendar ||--o{ calendar_holiday : "calendar_id (CASCADE)"
    work_calendar ||--o{ calendar_special_day : "calendar_id (CASCADE)"
    work_calendar ||--o{ calendar_assignment : "calendar_id (CASCADE)"
    tenant ||--o{ calendar_assignment : "tenant_id"
    tenant ||--o{ shift_template : "tenant_id"
    shift_template ||--o{ shift_assignment : "shift_template_id"
    app_user ||--o{ shift_assignment : "employee_id"

    work_calendar {
        uuid id PK
        uuid tenant_id FK
        varchar name UK "único por tenant"
        varchar timezone "IANA"
        date valid_from
        date valid_to
        varchar status "ACTIVE|ARCHIVED"
        bigint version
    }
    calendar_day_rule {
        uuid calendar_id PK,FK
        varchar day_of_week PK "MONDAY..SUNDAY"
        boolean working
        int expected_minutes
    }
    calendar_holiday {
        uuid calendar_id PK,FK
        date holiday_date PK
        varchar name
    }
    calendar_special_day {
        uuid calendar_id PK,FK
        date special_date PK
        varchar name
        int expected_minutes "0 = no laborable"
    }
    calendar_assignment {
        uuid id PK
        uuid tenant_id FK
        uuid calendar_id FK
        varchar scope "TENANT|TEAM|EMPLOYEE"
        uuid scope_target_id "opaco, sin FK; NULL solo en TENANT"
    }
    shift_template {
        uuid id PK
        uuid tenant_id FK
        varchar name UK "único por tenant"
        time start_time
        time end_time "puede ser menor: cruza medianoche"
        int planned_break_minutes
        varchar status "ACTIVE|ARCHIVED"
    }
    shift_assignment {
        uuid id PK
        uuid tenant_id FK
        uuid employee_id FK
        uuid shift_template_id FK
        date valid_from
        date valid_to "NULL = sin fin"
    }
```

Las tres colecciones del calendario usan **clave natural compuesta** y no un id
artificial: dentro de un calendario, un festivo se identifica por su fecha y una
regla semanal por su día. Eso convierte "no puede haber dos festivos el mismo
día" en la propia clave primaria.

La **resolución del calendario efectivo** depende de que haya como mucho una
asignación por ámbito. Se garantiza con dos índices únicos parciales, porque un
`UNIQUE` ordinario no deduplicaría los `NULL` del ámbito `TENANT`:

| Índice | Condición | Impide |
| --- | --- | --- |
| `ux_calendar_assignment_scoped` | `WHERE scope_target_id IS NOT NULL` | Dos asignaciones al mismo equipo o empleado |
| `ux_calendar_assignment_tenant_scope` | `WHERE scope = 'TENANT'` | Dos calendarios por defecto en el mismo tenant |

Un día concreto, la precedencia es `SPECIAL_DAY` → `HOLIDAY` → `WEEKLY_RULE`, y
`OUT_OF_VALIDITY` si la fecha cae fuera de `valid_from`/`valid_to`; el enumerado
`DaySource` deja constancia de cuál ganó. En turnos, `start_time > end_time` es
válido y significa que el turno **cruza medianoche**; el `CHECK` solo prohíbe
que ambos sean iguales (duración cero).

## Ausencias

```mermaid
erDiagram
    tenant ||--o{ absence_type : "tenant_id"
    absence_type ||--o{ absence_request : "absence_type_id"
    app_user ||--o{ absence_request : "employee_id"
    app_user ||--o{ absence_request : "resolved_by"

    absence_type {
        uuid id PK
        uuid tenant_id FK
        varchar code UK "único por tenant"
        varchar name
        boolean requires_approval
        boolean allows_attachment
        boolean active
    }
    absence_request {
        uuid id PK
        uuid tenant_id FK
        uuid employee_id FK
        uuid absence_type_id FK
        date start_date
        date end_date
        varchar status "PENDING|APPROVED|REJECTED|CANCELLED"
        uuid resolved_by FK
    }
```

Los tipos de ausencia son **por tenant**, no globales: cada organización define
su catálogo con su propio `code`. Las fechas son `DATE` y no instantes: una
ausencia cubre días de calendario. El `CHECK` `end_date >= start_date` es la
única restricción de rango; el solape entre ausencias del mismo empleado se
comprueba en la aplicación, no en el esquema, porque la regla depende del estado
(dos solicitudes `REJECTED` sí pueden solaparse).

## Traza y mensajería

```mermaid
erDiagram
    outbox_message ||--o{ processed_event : "event_id = outbox_message.id (sin FK)"

    outbox_message {
        uuid id PK
        uuid tenant_id "nullable: hechos de plataforma"
        varchar aggregate_type
        uuid aggregate_id "sin FK: apunta a varias tablas"
        varchar event_type
        int event_version
        jsonb payload
        varchar status "PENDING|PROCESSING|PUBLISHED|FAILED|DISCARDED"
        int attempts
        timestamptz next_attempt_at
    }
    processed_event {
        uuid event_id PK
        varchar consumer PK
    }
    notification {
        uuid id PK
        uuid tenant_id "sin FK"
        uuid recipient_user_id "sin FK"
        varchar recipient_email "desnormalizado a propósito"
        varchar type
        varchar status "PENDING|SENT|FAILED|CANCELLED|DISCARDED"
        boolean email_required
        varchar action_path
        timestamptz read_at "NULL = no leída"
    }
    audit_event {
        uuid id PK
        uuid tenant_id "nullable"
        uuid actor_user_id "nullable: acciones del sistema"
        varchar action
        varchar entity_type
        uuid entity_id "sin FK"
        uuid correlation_id
        jsonb metadata
    }
```

Cuatro tablas sin apenas relaciones declaradas, y por buenos motivos:

- **`outbox_message`** implementa el patrón *Transactional Outbox*
  ([ADR-0005](../adr/ADR-0005-transactional-outbox-sin-broker.md)): el evento se
  escribe en la **misma transacción** que el cambio de negocio, y un publicador
  lo entrega después. `aggregate_id` señala a la tabla que corresponda según
  `aggregate_type`, así que no puede tener clave ajena.
- **`processed_event`** deduplica por `(event_id, consumer)`, no solo por
  evento. La clave era únicamente `event_id` hasta `V23`; con varios
  consumidores, el primero que marcaba el evento dejaba a los demás creyendo
  que ya estaba procesado. `event_id` coincide con `outbox_message.id`.
- **`notification`** reúne el aviso en la aplicación (`read_at`) y su entrega
  por correo (`status`, `attempts`, `last_error`) en una sola fila: es el mismo
  hecho. `recipient_email` está **desnormalizado a propósito** —la notificación
  es una foto del momento, y cambiar de correo después no debe redirigir un
  aviso ya emitido— y `email_required` se persiste en lugar de calcularse
  porque la cola de envío filtra por SQL
  ([ADR-0018](../adr/ADR-0018-destinatarios-por-rol-y-politica-de-canal-de-notificacion.md)).
- **`audit_event`** es un registro de solo escritura, indexado por
  `(tenant_id, occurred_at DESC)` y por `correlation_id` para reconstruir todo
  lo ocurrido en una misma petición.

Tanto `outbox_message` como `notification` tienen el estado `DISCARDED`:
descartar desde el panel de plataforma **no borra la fila**, la deja con su
`last_error` intacto y deja de contarla como incidencia pendiente
([ADR-0020](../adr/ADR-0020-mantenimiento-manual-de-colas-fallidas.md)).
`DISCARDED` no es `CANCELLED`: cancelar anula un aviso pendiente que dejó de ser
relevante; descartar abandona el reintento de un envío que agotó sus intentos.
