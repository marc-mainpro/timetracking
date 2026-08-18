# ADR-0019: El rol EMPLOYEE decide quién es asignable, y la vista activa no es un permiso

* Estado: accepted
* Fecha: 2026-08-18

## Contexto y problema

`GET /api/v1/employees` devolvía todos los usuarios del tenant sin filtrar por
rol, y es el único listado que existe. Los tres desplegables de asignación del
frontend —calendario a un empleado, calendario efectivo, turno a un empleado—
bebían de él, así que ofrecían como destinatario a un `TENANT_ADMIN` que no
ficha. El backend tampoco lo impedía: `AssignShiftUseCase` solo comprobaba que
el usuario existiera y `AssignCalendarUseCase` ni siquiera eso para el ámbito
`EMPLOYEE`.

El resultado era un dato que no significa nada. Los endpoints de jornada
(`/api/v1/workdays/**`, `/api/v1/app/**`) exigen `hasRole('EMPLOYEE')`, de modo
que un turno asignado a un administrador que no lo es nunca llegaría a usarse:
nadie podría fichar contra él.

Al mismo tiempo se quiere lo contrario para quien sí ficha: un `TENANT_ADMIN`
puede además ser empleado, y entonces debe aparecer en esos desplegables. El
modelo ya lo permitía —`User` guarda un `Set<Role>` y el alta ofrece dos
casillas independientes—, pero la interfaz volcaba los dos menús a la vez, hasta
trece enlaces, sin manera de moverse entre una zona y otra.

## Decisión

### 1. El rol `EMPLOYEE` es el criterio de asignabilidad, y se aplica dos veces

En el **listado**, como filtro opcional: `GET /api/v1/employees?role=EMPLOYEE`.
No un endpoint nuevo de «asignables»: es el mismo recurso, la misma
autorización y la misma forma de respuesta, y duplicarlo crearía dos verdades
sobre quién es asignable. Al ser opcional, la gestión de usuarios sigue viendo
a todo el mundo, administradores puros incluidos, que es lo que necesita.

Filtrar por un rol de plataforma dentro de un tenant responde **400**: no tiene
significado, y devolver una lista vacía lo haría parecer una consulta legítima.

En las **asignaciones**, como regla de negocio. `AssignShiftUseCase` y
`AssignCalendarUseCase` (solo ámbito `EMPLOYEE`) rechazan un destinatario sin
ese rol. Filtrar la interfaz sin cerrar la API dejaría la regla como una
convención del frontend.

### 2. Un solo puerto en `shared`, y no dos consultas

`shared.application.EmployeeAssignmentTargetQuery` lo declara y
`identity.infrastructure.IdentityEmployeeAssignmentTargetQuery` lo implementa,
el mismo reparto que `TenantUsageQuery` y por el mismo motivo (ADR-0011): lo
preguntan **dos** módulos, así que declararlo en cada uno duplicaría el
contrato, y declararlo en `identity` le obligaría a mirar hacia quien pregunta.
De paso, `shift` deja de importar `identity.domain.UserRepository`, que era la
única dependencia directa de ese tipo entre módulos de negocio.

Devuelve un estado (`UNKNOWN` / `NOT_EMPLOYEE` / `ASSIGNABLE`) y no un par de
booleanos: las dos primeras respuestas llevan a códigos HTTP distintos y
separarlas en dos consultas abriría una carrera entre ambas.

### 3. Destinatario desconocido es 404; destinatario que no ficha, 409

`TargetNotEmployeeException` (`shared.domain`, código `TARGET_NOT_EMPLOYEE`) es
una violación de regla de negocio, así que el handler global ya la traduce a
**409** con Problem Details (ADR-0006). Que no exista sigue siendo **404**, como
hasta ahora. Vive en `shared` y no duplicada en `shift` y `calendar` porque es
la misma regla y el mismo código: el frontend lo traduce a un mensaje una sola
vez.

En calendarios la comprobación solo corre si hay destinatario. Un ámbito
`EMPLOYEE` que llega sin él es una petición incoherente, y de eso responde el
agregado con un 400 al construir la asignación; adelantarse lo convertiría en un
404 y se perdería el motivo real del rechazo.

### 4. `GET /calendar-assignments/effective` no valida el rol

Es una lectura. Si quien se consulta no es empleado simplemente no habrá
asignación de ámbito `EMPLOYEE` y regirá la de la organización, que es una
respuesta legítima (ADR-0017). Validar aquí convertiría en error un 200 correcto.

### 5. No se migra a los administradores existentes

Un administrador es empleado solo si se le marca ese rol explícitamente. La
alternativa —añadir `EMPLOYEE` a todos los `TENANT_ADMIN` actuales— haría
aparecer en los desplegables justo a quienes esta decisión quiere quitar de ahí,
y presupondría que todo el que administra también ficha.

### 6. La vista activa es presentación, nunca permiso

`ViewModeService` decide qué zona se está usando (`EMPLOYEE`, `ADMIN`,
`PLATFORM`) y el menú muestra solo la suya. Los guards siguen autorizando por
los roles reales del JWT: quien tiene ambos conserva el acceso a las dos zonas
aunque vea una, de modo que un enlace guardado de la otra sigue funcionando, y
la navegación sincroniza la vista con la ruta visitada para que el menú no
contradiga a la pantalla.

Ese servicio absorbe además la prioridad de rol que estaba repetida en el guard,
en la cabecera y en el login —y que además no coincidía entre ellos: el guard
anteponía `PLATFORM_ADMIN` y la cabecera `TENANT_ADMIN`—. Se adopta la del
guard, que es la que decidía las redirecciones reales.

La elección se guarda en `localStorage` bajo `tfp.view`, con el `sub` del JWT
por delante. En `sessionStorage` se perdería al cerrar el navegador, justo
cuando la sesión sobrevive —la cookie de refresh dura 14 días—. Guardar el
usuario evita heredar la vista de otra cuenta en un navegador compartido y hace
innecesario limpiar al cerrar sesión: un valor ajeno se ignora al leerlo. No
contradice el ADR-0004: es una preferencia de interfaz, no un dato de sesión.

## Consecuencias

* (+) Una asignación de turno o calendario apunta siempre a alguien que puede
  fichar contra ella.
* (+) La regla se cumple aunque la petición no venga de la interfaz.
* (+) Quien administra y además ficha ve un menú a la vez y puede cambiar de
  zona; el resto no ve ningún control nuevo.
* (+) La prioridad de rol deja de estar triplicada y divergente.
* (−) Los desplegables cargan dos listados: el filtrado los alimenta y el
  completo resuelve el nombre de quien recibió una asignación y ya no es
  empleado. Sin eso, esas filas mostrarían un UUID.
* (−) Quitar el rol `EMPLOYEE` a alguien no borra sus asignaciones previas, que
  quedan inertes. Retirarlas sería una decisión distinta —y destructiva— que
  esta no toma.
* (−) Sigue en pie el `size=100` fijo de los desplegables: con más de cien
  empleados truncan en silencio. El filtro lo alivia, porque los
  administradores puros dejan de ocupar cupo, pero no lo resuelve.
