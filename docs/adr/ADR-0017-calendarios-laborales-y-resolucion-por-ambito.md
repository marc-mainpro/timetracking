# ADR-0017: Calendarios laborales, resolución por ámbito y equipos sin gestión de equipos

* Estado: accepted
* Fecha: 2026-08-04

## Contexto y problema

La épica T70 introduce el módulo `calendar`: calendarios laborales con reglas
semanales, festivos, jornadas especiales y vigencia (RF-CAL-001..007), asignables
a toda la organización, a un equipo o a un empleado, con la regla «la asignación
más específica prevalece».

Al diseñarlo aparecieron tres decisiones que exceden la implementación de una
funcionalidad aislada, porque afectan a módulos que todavía no existen:

1. **La resolución por ámbito no es solo del calendario.** Turnos (T90) y
   ausencias (T80) necesitan saber qué calendario rige para un empleado en una
   fecha para validar solapes y computar días. Si cada uno la implementa por su
   cuenta, la regla de precedencia se duplica tres veces y diverge a la primera
   corrección.
2. **No existe el concepto de «equipo» en el sistema.** RF-CAL-006 exige el
   ámbito de equipo, pero no hay entidad, tabla, ni gestión de pertenencia, y
   construirla dentro de T70 desbordaría el alcance de la tarea y pisaría el
   terreno de una épica futura.
3. **Un calendario opera sobre fechas locales, no sobre instantes.** RNF-011
   obliga a guardar los instantes en UTC y RNF-012 da a cada tenant una zona IANA.
   Aplicar esas reglas sin matizar llevaría a guardar «6 de enero» como un
   `Instant`, que es un error de modelado: un festivo no ocurre a una hora.

## Decisión

### 1. La precedencia por ámbito vive en un único servicio de dominio

`calendar.domain.service.EffectiveCalendarResolver` implementa la regla completa
y es **el contrato** que consumen los demás módulos, directamente o a través de
`ResolveEffectiveCalendarUseCase`. Ningún otro módulo debe reimplementarla.

El orden lo declara `AssignmentScope.specificity()` (`TENANT` 10, `TEAM` 20,
`EMPLOYEE` 30), con valores no contiguos para poder intercalar un ámbito futuro
—p.ej. centro de trabajo— sin renumerar ni alterar el orden ya publicado.

El algoritmo, en este orden: **aplicabilidad** (¿la asignación apunta a este
empleado, a su equipo o al tenant?), **disponibilidad** (¿su calendario existe,
está activo y vigente *esa fecha*?), **precedencia** (mayor especificidad) y
**desempate** determinista.

Dos consecuencias que son decisiones y no efectos colaterales:

* Un calendario archivado o fuera de vigencia **no bloquea su ámbito**: la
  resolución cae al siguiente ámbito menos específico que sí tenga calendario
  disponible. La alternativa —fallar— dejaría a un empleado sin calendario por
  archivar el suyo, cuando el de la organización sigue siendo una respuesta
  legítima.
* «Ningún calendario aplica» es un resultado válido (`Optional.empty()`), no un
  error. Qué hacer entonces (rechazar la operación, asumir jornada estándar) es
  decisión de quien pregunta, no del calendario.

El desempate compara `id.toString()` y no los `UUID`: `UUID.compareTo` compara
los bits como `long` **con signo**, así que un id que empieza por `ffffffff` se
ordena antes que uno que empieza por `00000000`. Ese orden no coincide con el que
ve un humano ni con `ORDER BY id` en PostgreSQL. El desempate no debería
dispararse nunca —lo impide la unicidad por `(tenant, ámbito, destinatario)`—
pero si se dispara debe ser explicable.

### 2. El equipo es un identificador opaco que aporta quien llama

`CalendarAssignment` de ámbito `TEAM` guarda un `scope_target_id` **sin clave
ajena**: este módulo no valida el equipo ni consulta pertenencias. Quien resuelve
el calendario efectivo pasa el `teamId` del empleado, o `null` si no lo conoce, y
en ese caso las asignaciones de equipo quedan descartadas.

Esto permite cumplir RF-CAL-006 hoy sin construir gestión de equipos, y deja el
camino abierto: cuando exista un módulo de equipos bastará con resolver el
`teamId` dentro de `ResolveEffectiveCalendarUseCase`, sin tocar el dominio ni el
contrato que ya consumen turnos y ausencias.

### 3. Fechas locales en el dominio, instantes solo en los bordes

La vigencia, los festivos y las jornadas especiales son `LocalDate`, y las
columnas correspondientes son `DATE`. Solo `created_at`/`updated_at` —auditoría
técnica— son `TIMESTAMPTZ`. El calendario guarda su propia zona IANA (normalmente
la del tenant, pero propia para permitir una organización multi-sede) y expone
`startOfDay(fecha)`, `endOfDayExclusive(fecha)` y `civilDayLength(fecha)` como
**único** punto de conversión a la línea temporal.

La jornada esperada de un día **no** cambia porque ese día civil dure 23 o 25
horas por el cambio de horario de verano: es una regla del calendario laboral, no
una medida de tiempo transcurrido. La conversión a instantes sí refleja la
duración real, y quien calcule ventanas debe usar esos métodos en vez de sumar
24 h.

### 4. Decisiones de alcance menores, pero deliberadas

* **Los festivos y las jornadas especiales son objetos de valor sin id**, con
  clave primaria `(calendar_id, fecha)`. Dentro de un calendario se identifican
  por su fecha; un id artificial no aportaría nada y permitiría duplicados.
* **Una fecha no puede ser a la vez festivo y jornada especial**
  (`CALENDAR_DUPLICATE_DATE`): con las dos presentes la precedencia dejaría de
  ser explicable al usuario.
* **Editar es sustituir por completo** reglas, festivos y jornadas especiales
  (semántica `PUT`). Una API de parcheo por elemento complicaría la concurrencia
  sin servir mejor al caso real, que es editar el calendario en un formulario.
* **Las asignaciones no tienen vigencia propia.** La dimensión temporal vive en
  el calendario: programar un cambio de horario se hace creando un calendario con
  la nueva vigencia. Con dos periodos de validez —el de la asignación y el del
  calendario— los solapes se vuelven ambiguos.
* **Archivar es el borrado de un calendario** (`DELETE` responde 204 y marca
  `ARCHIVED`): las jornadas ya calculadas y las asignaciones históricas lo
  referencian. Una asignación, en cambio, sí se borra físicamente: su valor
  histórico queda en el evento de integración y en auditoría.

## Consecuencias

* (+) La regla de precedencia se corrige en un solo sitio y los tres módulos que
  la usan heredan la corrección.
* (+) T70 se cierra sin bloquearse a la espera de una épica de equipos.
* (+) El modelo temporal es correcto en los cambios de horario, verificado con
  pruebas de días de 23 h y 25 h en `Europe/Madrid`.
* (−) Mientras no exista gestión de equipos, el ámbito `TEAM` solo es utilizable
  por integraciones que ya conozcan el id de equipo; desde la UI hay que
  escribirlo a mano. Es una limitación consciente y visible.
* (−) `scope_target_id` no tiene integridad referencial: puede quedar apuntando a
  un empleado borrado. Se acepta porque la resolución simplemente no encontraría
  a quién aplicar, y añadir una FK a `app_user` acoplaría el esquema de
  `calendar` al de `identity`.
* (−) La sustitución completa al editar hace que dos administradores que editen
  a la vez se pisen el último cambio. Lo mitiga el bloqueo optimista (`version`),
  que devuelve 409 en vez de perder datos en silencio.

## Alternativas descartadas

* **Que cada módulo consulte las tablas de calendario y aplique la precedencia
  por su cuenta**: es lo que la regla «la más específica prevalece» invita a
  hacer, y es exactamente cómo tres implementaciones acaban discrepando en los
  casos raros (calendario archivado, empleado sin equipo, fecha fuera de
  vigencia), que son justo los que importan.
* **Construir un módulo de equipos dentro de T70**: fuera de alcance, y decidir
  el modelo de equipos (jerárquicos o planos, pertenencia múltiple o única)
  presionado por una necesidad de calendarios daría un mal modelo de equipos.
* **Guardar la vigencia y los festivos como `TIMESTAMPTZ`**: uniformaría el
  esquema a costa de introducir una hora arbitraria en un dato que no la tiene, y
  haría que un festivo «cambiara de día» según la zona del consumidor.
* **Dar vigencia propia a la asignación**: más expresivo sobre el papel, pero
  obliga a resolver solapes entre dos periodos de validez en cada consulta, para
  un caso de uso que el periodo del calendario ya cubre.
