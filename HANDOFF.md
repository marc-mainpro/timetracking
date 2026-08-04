# HANDOFF — A3a / T70 Calendarios laborales

Módulo nuevo `calendar` (backend + frontend). Ola 1.

Reservas usadas: **migración V16** (V17 sin usar, los huecos son aceptables),
**ADR-0013**. No se ha tocado `timetracking` ni ningún fichero prohibido.

---

## 0. AVISO IMPORTANTE SOBRE LA BASE DEL WORKTREE

El worktree se creó a partir de **`main` (6370dd2)**, que **no** contiene el
trabajo de la Ola 0 (módulo `platform`, ADR-0011/0012, `IntegrationEventMapper`,
`PublicEndpointsContributor`, `config/*.yml` en `application.yml`, migraciones
V10/V11 ni los documentos de requisitos/diseño/tareas de la V2). Sobre esa base
la tarea era literalmente irrealizable: los puntos de contribución que las
instrucciones exigen usar no existían.

Se corrigió con `git reset --hard 021acd0`, la punta de
**`feat/v2-ola0-preparacion`** / **`feat/v2-ola1-registro-seguridad-calendarios`**,
que es la base correcta para la Ola 1. **Al mergear, verificar que el resto de
agentes de la ola partieron de ese mismo commit**; si alguno partió de `main`, su
entrega tendrá el mismo problema.

---

## 1. Ficheros prohibidos: cambios que necesito que apliquéis

### `docs/api/openapi.yaml`

Nada que aplicar a mano. Los controladores llevan `@Tag`, `@Operation` y
`@Schema`; basta con reexportar `/v3/api-docs.yaml` al cerrar la ola. Los `@Tag`
nuevos son **`Admin - Calendars`** y **`Admin - Calendar assignments`**.

### `backend/pom.xml`, `frontend/package.json`, lockfiles

Ninguna dependencia nueva.

### `docker-compose*.yml`, `.env.example`

Ningún servicio ni puerto nuevo. Tres variables de entorno **opcionales** (todas
con valor por defecto en `config/calendar.yml`, así que el sistema arranca sin
ellas). Si queréis exponerlas en compose:

```
CALENDAR_MAX_HOLIDAYS=400
CALENDAR_MAX_SPECIAL_DAYS=400
CALENDAR_DEFAULT_TIMEZONE=Europe/Madrid
```

### `.github/workflows/ci.yml`

Nada. Las suites nuevas las recoge el `mvn -B verify` y el `npm run
test:coverage` existentes.

### `application.yml`

Nada. El `spring.config.import` de `optional:classpath:config/calendar.yml` ya
estaba declarado (ADR-0011).

### `SecurityConfig`

Nada. Todos los endpoints de calendario son autenticados y con rol; no hay rutas
públicas, así que no hace falta `PublicEndpointsContributor`.

### Tests de `architecture/` (los 7 intocables)

**Nada que añadir**, y es deliberado. `LayeredArchitectureTest` exige una
excepción explícita por cada par `(*Controller, tipo de dominio)`. Para no
necesitar ninguna:

- `CalendarStatus` se traduce en `CalendarRestMapper.toStatus(String)` y se
  encadena sin variable intermedia en el controlador (la regla por convención de
  ADR-0011 permite `*RestMapper -> ..domain..`).
- `ResolveEffectiveCalendarUseCase` expone `resolveView(...)`, que devuelve
  `Optional<EffectiveCalendarView>` (DTO de `application`) además del
  `resolve(...)` que devuelve el agregado, para que el controlador nunca toque
  `EffectiveCalendar`.

Si alguien "simplifica" eso metiendo una variable local tipada con un tipo de
dominio en un controlador, el build romperá y hará falta editar un fichero
intocable. Está comentado en el código.

---

## 2. Configuración

Fichero nuevo `backend/src/main/resources/config/calendar.yml`:

| Propiedad | Defecto | Para qué |
|---|---|---|
| `calendar.max-holidays-per-calendar` | 400 | Cota defensiva de entrada, no regla de negocio |
| `calendar.max-special-days-per-calendar` | 400 | Igual |
| `calendar.default-timezone` | `Europe/Madrid` | Zona aplicada si el cliente crea un calendario sin indicarla |

Puertos: ninguno nuevo.

---

## 3. Eventos publicados

Todos vía Outbox, dados de alta solo por ser `CalendarIntegrationEventMapper` un
`@Component` (**no se ha tocado `OutboxDomainEventPublisher`**). Documentados en
`docs/integration/event-catalog.md`.

| `eventType` | `aggregateType` | Quién debería consumirlo |
|---|---|---|
| `calendar.calendar-created.v1` | `WorkCalendar` | Informes (T100) para etiquetar periodos |
| `calendar.calendar-updated.v1` | `WorkCalendar` | Quien cachee jornadas esperadas: invalidar |
| `calendar.calendar-archived.v1` | `WorkCalendar` | Turnos (T90) y ausencias (T80): revalidar afectados |
| `calendar.calendar-assigned.v1` | `CalendarAssignment` | Turnos y ausencias: invalidar resolución cacheada |
| `calendar.calendar-assignment-removed.v1` | `CalendarAssignment` | Igual; el empleado puede pasar a un calendario menos específico |

Las fechas de vigencia viajan como `YYYY-MM-DD` (fecha local), **no** como
instante. El payload lleva la cabecera del calendario, no el detalle de reglas y
festivos: quien lo necesite lo relee por la API.

---

## 4. CONTRATO DE RESOLUCIÓN POR ÁMBITO — leer antes de B3 (turnos) y C1 (ausencias)

Esta es la parte del entregable que **otros módulos van a consumir**. Está en
ADR-0013 y en `docs/domain/reglas-de-negocio.md`.

### Qué usar

```java
// Opción A — desde un caso de uso, con acceso a repositorios:
@Service
class MiCasoDeUso {
    private final ResolveEffectiveCalendarUseCase resolveEffectiveCalendar;

    Optional<EffectiveCalendar> calendario =
        resolveEffectiveCalendar.resolve(employeeId, teamId /* nullable */, fecha);
}

// Opción B — si ya tenéis cargados los agregados (evita releer):
Optional<EffectiveCalendar> calendario = EffectiveCalendarResolver.resolve(
        assignments, calendars, employeeId, teamId, fecha);
```

`EffectiveCalendar` da: `calendar()`, `assignment()`, `scope()`, `working()`,
`expectedHours()` (`Duration`) y `day().source()` (por qué regla se decidió).

### Reglas que ya están resueltas y NO debéis reimplementar

1. **Precedencia**: empleado > equipo > tenant, declarada en
   `AssignmentScope.specificity()` (30/20/10, no contiguos para poder intercalar
   un ámbito futuro sin renumerar).
2. **Un calendario archivado o fuera de vigencia no bloquea su ámbito**: la
   resolución cae al siguiente ámbito menos específico que sí tenga calendario
   disponible. No falla.
3. **`Optional.empty()` es una respuesta legítima**, no un error: significa que
   ningún calendario cubre a ese empleado en esa fecha. **Decidid vosotros** qué
   hacer (rechazar, asumir jornada estándar) y documentadlo; el calendario no
   impone una política.
4. **Desempate determinista** por `id.toString()` si hubiera empate de ámbito
   (no debería: hay índice único). Ojo: **no** es `UUID.compareTo`, que compara
   con signo y ordenaría `ffffffff...` antes que `00000000...`.

### Lo que TENÉIS que aportar vosotros: `teamId`

**No hay gestión de equipos en el sistema** (ADR-0013). `CalendarAssignment` de
ámbito `TEAM` guarda un `UUID` opaco sin clave ajena, y este módulo no sabe a qué
equipo pertenece un empleado.

- Si pasáis `teamId = null`, las asignaciones de equipo **se descartan**
  silenciosamente (no casan por defecto), y el empleado resuelve a su asignación
  personal o a la del tenant.
- Cuando exista un módulo de equipos, se resolverá el `teamId` dentro de
  `ResolveEffectiveCalendarUseCase` **sin cambiar esta firma**. Programad contra
  ella con confianza.

### Fechas locales, no instantes

`resolve(...)` toma una `LocalDate`. Si partís de un `Instant` (una jornada, un
turno), convertidlo antes a la fecha local **de la zona del calendario o del
tenant**, no de la del servidor. Para ventanas de día usad
`WorkCalendar.startOfDay(fecha)` / `endOfDayExclusive(fecha)`, que respetan los
días de 23 h y 25 h del cambio horario; no sumar 24 h a mano.

---

## 5. API añadida

```
POST   /api/v1/admin/calendars
GET    /api/v1/admin/calendars?page&size&status
GET    /api/v1/admin/calendars/{calendarId}
PUT    /api/v1/admin/calendars/{calendarId}
DELETE /api/v1/admin/calendars/{calendarId}          -> 204, archivado lógico

POST   /api/v1/admin/calendar-assignments
GET    /api/v1/admin/calendar-assignments?page&size&calendarId
DELETE /api/v1/admin/calendar-assignments/{assignmentId}
GET    /api/v1/admin/calendar-assignments/effective?employeeId&date&teamId
```

Todos exigen `TENANT_ADMIN`. Las asignaciones cuelgan de una ruta base propia y
no de `/api/v1/admin/calendars/...` para no competir con el patrón
`/{calendarId}` y dejar la API a merced del orden de resolución de patrones de
Spring MVC.

`errorCode` nuevos (todos → 409 salvo indicación): `CALENDAR_ARCHIVED`,
`CALENDAR_NAME_ALREADY_EXISTS`, `CALENDAR_DUPLICATE_DAY_RULE`,
`CALENDAR_DUPLICATE_DATE`, `CALENDAR_ASSIGNMENT_ALREADY_EXISTS`.

**Frontend:** ruta `admin/calendars` (insertada en orden alfabético en
`app.routes.ts`) y enlace «Calendarios» en el bloque de admin de
`app.component.html`.

---

## 6. Riesgos y cosas asumidas

- **Ámbito `TEAM` semi-utilizable.** Sin gestión de equipos, desde la UI hay que
  escribir el id de equipo a mano. Es consciente (ADR-0013), pero si producto
  espera usar equipos en la demo, hace falta la épica de equipos antes.
- **`scope_target_id` sin integridad referencial.** Puede quedar apuntando a un
  empleado borrado; la resolución simplemente no encontraría a quién aplicar.
  Añadir una FK a `app_user` acoplaría el esquema de `calendar` al de `identity`.
- **Editar un calendario sustituye reglas, festivos y jornadas especiales por
  completo** (semántica `PUT`). Dos administradores editando a la vez se pisan;
  lo mitiga el bloqueo optimista (`version` → 409), que no pierde datos en
  silencio, pero la UI no ofrece merge.
- **Las jornadas especiales son de fecha única, no de rango.** Un horario
  intensivo de verano hay que darlo de alta día a día (o crear un calendario con
  su propia vigencia, que es la vía recomendada). Si se demuestra molesto,
  añadir rangos es compatible hacia atrás.
- **Choque previsible con C2 (Ola 3).** El cálculo horario real de las jornadas
  se integra ahí. Este módulo **no toca `timetracking`**: expone
  `expectedHours(fecha)` y el calendario efectivo, y ahí acaba su
  responsabilidad.
- **Choque previsible con B2 (T72, reglas horarias).** Jornada máxima y descanso
  obligatorio son de B2 y viven fuera del calendario. Si B2 necesita la jornada
  esperada del día, que la pida por este contrato en vez de duplicar reglas
  semanales.
- **V17 sin usar.** Queda libre por si alguien la necesita, aunque la reserva
  dice que no se invadan bloques ajenos.

---

## 7. Verificación

- `mvn -B verify` → **BUILD SUCCESS**. «All coverage checks have been met» en las
  dos reglas JaCoCo (dominio 90 %, aplicación 80 %).
- `npx ng lint` → «All files pass linting».
- `npm run test:coverage` → 95 tests SUCCESS. Cobertura global **subió**:
  statements 73,94 % (umbral 65), branches 67,33 % (58), functions 66,25 % (55),
  lines 74,77 % (65).
- `npx ng build` → bundle generado sin errores.

Suites nuevas: `WorkCalendarTest`, `WorkCalendarDaylightSavingTest`,
`CalendarValueObjectsTest`, `CalendarAssignmentTest`,
`EffectiveCalendarResolverTest`, `WorkCalendarUseCasesTest`,
`CalendarAssignmentUseCasesTest`, `CalendarIntegrationEventMapperTest`,
`FlywayCalendarMigrationIntegrationTest`,
`CalendarRepositoryAdapterIntegrationTest`,
`AdminCalendarControllerIntegrationTest`,
`AdminCalendarAssignmentControllerIntegrationTest`,
`admin-calendars.component.spec`, `calendars.service.spec`.
