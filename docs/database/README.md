# Base de datos

Documentación del esquema relacional de Timetracking: qué tablas existen, cómo
se relacionan y por qué el modelo es como es.

| Documento | Contenido |
| --- | --- |
| [Relaciones y diagramas](relaciones.md) | Mapa global y diagramas entidad-relación por módulo |
| [Diccionario de datos](diccionario-de-datos.md) | Tabla por tabla: columnas, tipos, restricciones e índices |
| [Historial de migraciones](migraciones.md) | Qué introduce cada `V<N>__*.sql` y cómo añadir una nueva |

Vistas complementarias: [`docs/domain/agregados.md`](../domain/agregados.md)
describe el modelo desde el dominio (agregados, invariantes y transiciones de
estado); este directorio lo describe desde la persistencia. Cuando ambos hablan
de lo mismo, el dominio manda: la base de datos es su reflejo, no al revés.

## Motor y gestión del esquema

- **PostgreSQL 16.** No se usa ninguna extensión: el esquema es SQL estándar
  más los índices parciales y el tipo `JSONB` de PostgreSQL.
- **Un único esquema lógico** (`public`) y **una única base de datos** para
  todos los tenants. El aislamiento es por columna, no por esquema
  ([ADR-0002](../adr/ADR-0002-multitenancy-columna-tenant-id.md)).
- **Flyway** es el único autorizado a modificar el esquema. Las migraciones
  viven en `backend/src/main/resources/db/migration` y se aplican al arrancar
  (`spring.flyway.enabled: true`).
- **Hibernate nunca crea ni altera tablas**: `spring.jpa.hibernate.ddl-auto` es
  `validate` en todos los perfiles. Si una entidad y su tabla divergen, la
  aplicación no arranca. Ese fallo es intencionado: obliga a que todo cambio de
  modelo pase por una migración.

## Convenciones del esquema

| Convención | Regla |
| --- | --- |
| Nombres | `snake_case` en tablas y columnas; singular en el nombre de tabla (`workday`, no `workdays`) |
| Claves primarias | `UUID` generado por la aplicación, nunca `SERIAL`/`IDENTITY`. Las tablas de detalle usan clave natural compuesta cuando la tienen (`calendar_holiday (calendar_id, holiday_date)`) |
| Instantes | `TIMESTAMPTZ`, siempre en UTC, mapeados a `Instant` |
| Fechas locales | `DATE` cuando el concepto es una fecha de calendario y no un punto en la línea temporal (festivos, vigencias, rangos de ausencia) |
| Duraciones | Enteros de **minutos** (`expected_minutes`, `worked_minutes`), nunca intervalos ni decimales |
| Enumerados | `VARCHAR` con `CHECK` explícito, no tipos `ENUM` de PostgreSQL: añadir un valor es un `ALTER … CHECK` y no una migración de tipo |
| Prefijos | `pk_`, `fk_`, `uq_`/`ux_`, `ix_`, `ck_` para primarias, ajenas, únicos, índices y restricciones de comprobación |
| Bloqueo optimista | Columna `version BIGINT` en los agregados con escritura concurrente (`workday`, `correction_request`, `work_calendar`) |

## Multitenancy

Casi toda tabla de negocio lleva `tenant_id` y **toda consulta se filtra por
él**, incluso cuando podría deducirse por un `JOIN`. El valor procede siempre
del token de la petición, nunca del cuerpo ni de la URL.

Esto explica dos rasgos del esquema que de otro modo parecerían redundancia o
descuido:

- **`tenant_id` duplicado en tablas hijas.** `account_lockout` o
  `workday_evaluation` guardan el tenant aunque su padre ya lo tenga: permite
  que cualquier lectura sea *tenant-scoped* sin un `JOIN`, y que el índice
  compuesto empiece por `tenant_id`.
- **Índices que empiezan por `tenant_id`.** Es la primera columna de casi todos
  los índices compuestos porque es el primer filtro de casi toda consulta.

Un `tenant_id` en `NULL` solo aparece donde el hecho puede ser de plataforma y
no de una organización: `audit_event` y `outbox_message`.

El **tenant de plataforma** (`00000000-0000-0000-0000-000000000001`, insertado
por `V11`) es una fila real de `tenant` a la que pertenecen los usuarios
`PLATFORM_ADMIN`. Se excluye por id de los listados de organizaciones.

## Claves ajenas: dónde hay y dónde no

No todas las relaciones están declaradas como `FOREIGN KEY`. La ausencia es
deliberada en tres casos, y conviene conocerlos antes de leer los diagramas:

1. **`tenant_id` en tablas de alto volumen o de traza** (`workday`,
   `correction_request`, `notification`, `audit_event`, `outbox_message`) no
   tiene clave ajena. Son tablas de escritura frecuente y el tenant ya está
   garantizado por el camino de aplicación; la comprobación por fila no aporta
   integridad real.
2. **Tablas técnicas sin relación de negocio**: `outbox_message`,
   `processed_event` y `audit_event` referencian agregados por id
   (`aggregate_id`, `entity_id`) sin apuntar a una tabla concreta, porque el
   mismo campo señala a agregados distintos según el tipo.
3. **`calendar_assignment.scope_target_id`** es un identificador **opaco**: en
   ámbito `EMPLOYEE` es un usuario y en ámbito `TEAM` es un equipo que el
   sistema no modela ([ADR-0017](../adr/ADR-0017-calendarios-laborales-y-resolucion-por-ambito.md)).
   Una clave ajena obligaría a elegir una de las dos tablas.

Donde sí hay clave ajena y el hijo carece de sentido sin el padre, el borrado
es `ON DELETE CASCADE` (`break_entry`, las colecciones del calendario,
`workday_evaluation`, `account_lockout`, `password_reset_token`). En el resto,
el borrado del padre está impedido a propósito: la traza histórica no debe
poder desaparecer por un borrado accidental.

## Datos personales

Tres decisiones del esquema tienen que ver con minimización de datos, no con el
modelo:

- **Nunca se guarda un token en claro.** `refresh_token`,
  `password_reset_token` y `tenant_registration` almacenan un **SHA-256 en
  hexadecimal** (64 caracteres). Un volcado de estas tablas no permite
  construir un enlace ni una sesión válidos.
- **Nunca se guarda una IP ni un `User-Agent` en claro.** `user_session` y
  `tenant_registration` guardan `ip_hash` y `user_agent_hash`.
- **La contraseña se guarda como hash BCrypt** en `app_user.password_hash` y,
  antes de existir el usuario, en `tenant_registration.owner_password_hash`.

Procedimientos de copia de seguridad cifrada y restauración en
[`docs/manuals/backup-restore.md`](../manuals/backup-restore.md).
