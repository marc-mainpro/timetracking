# Auditoría técnica (T10-01)

Fecha: 2026-08-07.

## Nota sobre el momento de esta auditoría

El plan situaba T10-01 al principio, antes de construir la V2: revisar el MVP,
clasificar defectos y convertir los bloqueantes en tareas. **No se ejecutó
entonces.** El desarrollo avanzó sin ella y este documento se escribe al cierre.

Se redacta igualmente, y no se marca la tarea como cancelada, porque su valor no
era solo priorizar: es dejar constancia de qué estaba roto y por qué no se veía.
Lo que sigue son defectos **reales, encontrados y corregidos**, no una revisión
teórica. Cada uno cita el commit que lo cierra.

La lección que deja el conjunto es la que ordena el documento: **ninguno de los
defectos graves era visible desde dentro de su propio módulo**. Aparecieron al
ejercitar el sistema entero, al medirlo con datos o al obligar a una regla a
demostrarse.

## Críticos

### Los mensajes del Outbox se marcaban como publicados sin haberse procesado

`LoggingIntegrationEventPublisher` registraba el fallo de un consumidor y marcaba
el mensaje `PUBLISHED` igualmente. Los reintentos que ADR-0012 daba por
garantizados **nunca se ejecutaban**: un SMTP caído perdía en silencio los
correos de verificación de alta y de recuperación de contraseña.

La justificación original era razonable —«la publicación no debe depender del
consumidor de demostración»— y dejó de serlo en cuanto aparecieron consumidores
reales, sin que nadie revisara la decisión. *Cerrado en `09808b7`.*

### La deduplicación no distinguía consumidores

`processed_event` tenía clave primaria `event_id` a secas, diseñada cuando solo
existía el consumidor de demostración. Con tres consumidores, **el primero en
marcar un evento dejaba a los demás creyendo que ya estaba procesado** y sus
efectos no se aplicarían jamás.

Era una bomba de relojería independiente: no fallaba, simplemente dejaba de
hacer trabajo. Clave compuesta `(event_id, consumer)` en V23. *Cerrado en
`09808b7`.*

## Altos

### El login era un oráculo de existencia de cuentas

El estado de cuenta y tenant se comprobaba antes que la contraseña, así que el
`errorCode` distinguía un correo inexistente de uno real desactivado. Además, un
correo desconocido respondía sin ejecutar BCrypt y **el tiempo de respuesta
delataba la inexistencia** aunque el cuerpo fuese idéntico.

La misma clase documentaba la propiedad contraria para el bloqueo de cuenta: la
garantía estaba cumplida a medias. *Cerrado en `bc3a362`.*

### Las ausencias eran inalcanzables para cualquier tenant

Los tipos de ausencia son *tenant-scoped*, ninguna migración los sembraba y no
existe endpoint que los cree. Todo tenant nacía con el catálogo vacío, de modo
que **ningún empleado podía solicitar una ausencia**. RF-ABS-001 estaba escrito,
modelado, con API y con pruebas —que creaban sus tipos a mano—. *Cerrado en
`2b667e4`.*

### El alta pública creaba tenants operativos de un paso

`POST /api/v1/auth/register` seguía llamando a `Tenant.register()`, saltándose la
verificación y la aprobación que introdujo la V2. Sobrevivió porque
`TestTenantFactory` lo usaba para levantar tenants en doce suites: andamiaje de
pruebas sosteniendo una puerta de producción. *Cerrado en `65bc875`.*

### Los informes recorrían las pausas de todos los tenants

`break_entry` no tenía índice utilizable por `workday_id`: el único existente es
parcial y cubre las pausas *abiertas*, mientras que los informes leen las
*cerradas*. El informe de un empleado y un mes leía 100.023 filas para devolver
30. El coste crecía con el tamaño total de la tabla, así que **el informe de un
tenant se degradaba a medida que otros acumulaban datos**. *Cerrado en `f8c77e4`.*

## Medios

| Defecto | Por qué no se veía | Cierre |
|---|---|---|
| Un parámetro mal formado devolvía 500 en vez de 400 | Sin manejador, cualquier error de conversión parecía caída del servidor y ensuciaba las métricas de error | `4c78522` |
| Una ruta inexistente bajo `/api/v1` devolvía 500 en vez de 404 | Igual: un error de URL parecía una caída | `4c78522` |
| `WorkdayRestMapper` inyectaba un repositorio; `SessionController` mapeaba el dominio | Las reglas de ArchUnit existían pero el build estaba en rojo y nadie las leía | `0b09a46` |
| El listado de correcciones no tenía índice por tenant | Mismo patrón que las pausas, a menor escala | `f8c77e4` |
| La auditoría no cubría jornadas ni turnos | El criterio global «la auditoría sea completa» pasaba porque la auditoría funciona; el plan pedía seis tipos de operación y había cuatro | este cierre |
| Producción arrancaría con logs en texto plano | `docker-compose` fija `SPRING_PROFILES_ACTIVE: local` y el override de producción no lo cambia | declarado, sin cerrar |
| `PUBLIC_REGISTRATION_ENABLED` y `APP_REQUEST_MAX_PAYLOAD_BYTES` no llegaban al contenedor | Estaban en `.env.example` pero no en el compose; rompía `smoke.sh` con la configuración por defecto | `021acd0` |

## Bajos

- **Doble aprobación de una corrección** devolvía `WORKDAY_ALREADY_CLOSED` en vez
  de `CORRECTION_ALREADY_RESOLVED`: el caso de uso ajustaba la jornada antes de
  resolver la corrección, y el cliente recibía el síntoma en lugar de la causa.
  *Cerrado en `2b667e4`.*
- **`scripts/smoke.sh` imprimía credenciales** por consola. *Cerrado en `012e343`.*
- **Cobertura de `shift.domain` por debajo del umbral**, oculta tras tests
  fallidos que abortaban el build antes de llegar a la comprobación. *Cerrado en
  `0b09a46`.*
- **Enlaces rotos a ADR-0013** introducidos por una renumeración automática mía.
  *Cerrado en `5bd75a3`.*

## Deuda preventiva convertida en regla

Dos casos en los que el estado actual era correcto pero nada impedía romperlo:

- **Autorización en endpoints privilegiados.** La cadena solo garantiza
  `anyRequest().authenticated()`: un controlador nuevo bajo `/admin` o
  `/platform` sin `@PreAuthorize` quedaría abierto a cualquier usuario
  autenticado y respondería 200 sin que nada fallara.
  `PrivilegedEndpointsRequireRoleTest` lo impide. *`b73bfd5`.*
- **Puntos de contribución entre módulos.** Cuatro ficheros obligaban a todo
  módulo nuevo a editar código de otro para darse de alta, y uno de ellos rompía
  el build de cualquiera que creara un `*RestMapper`. *`5d6e7fd`, ADR-0011.*

## Qué cambiaría del proceso

1. **Los criterios de aceptación de producto no sustituyen a la lista de
   tareas.** «La auditoría sea completa» pasaba mientras faltaban dos de los seis
   tipos de operación exigidos. Validar contra el resumen y no contra el detalle
   es cómo se cuela lo que falta.
2. **Un build en rojo silencia todo lo que viene después.** El umbral de
   cobertura incumplido llevaba tiempo oculto tras tres tests fallidos.
3. **Las decisiones caducan.** «El fallo del consumidor no debe propagarse» era
   correcta con un consumidor de demostración y peligrosa con consumidores
   reales, y nadie revisó la premisa al cambiar el contexto.
