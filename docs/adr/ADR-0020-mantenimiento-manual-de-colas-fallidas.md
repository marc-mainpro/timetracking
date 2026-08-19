# ADR-0020: Mantenimiento manual de las colas fallidas

* Estado: accepted
* Fecha: 2026-08-19

## Contexto y problema

El panel de estado de plataforma (`/platform/system-status`) informaba de cuántos
elementos habían agotado sus reintentos en cada cola (`outbox`, `notifications`),
pero no permitía hacer nada al respecto. Como lo que llega a `FAILED` no se
reintenta nunca más por sí solo y no produce ningún error visible para el usuario
final, la única forma de ver el detalle o de recuperar un mensaje era consultar la
base de datos a mano.

El caso de uso de reintento manual existía desde T703 (`RetryFailedOutboxMessage`)
pero **nunca se expuso por REST**, de modo que era inalcanzable desde la interfaz.

Hacía falta, por tanto, poder **ver** cada elemento fallido, **reintentarlo** y
**suprimirlo**. Y la supresión planteaba la pregunta de fondo: qué significa
"suprimir" algo cuya única utilidad, una vez que se renuncia a él, es dejar
constancia de que falló.

## Decisión

1. **Un puerto de contribución, no un controlador por módulo.**
   `FailedQueueMaintenance` vive en `outbox.application`, junto a
   `QueueStatusContributor`, y cada módulo aporta su implementación. Un único
   `FailedQueueController` sirve todas las colas. La alternativa —un controlador
   en cada módulo— duplicaría paginación, validación, auditoría y pruebas de
   seguridad, y obligaría al frontend a saber qué módulo posee cada cola. La
   dirección de la dependencia sigue siendo `notification -> outbox`, que es la
   que ya permite `ModuleCyclesTest`; el puerto es hermano del que ADR-0011
   introdujo por el mismo motivo.

   Se mantiene **separado** de `QueueStatusContributor`: una cola puede querer
   informar de su estado sin admitir intervención. La correspondencia por nombre
   entre ambos la vigila un test, porque una convención de cadenas se rompe en
   silencio y dejaría una cola con fallos y sin forma de resolverlos.

2. **Estado `DISCARDED` nuevo, no reutilizar `CANCELLED`.**
   En notificaciones, `CANCELLED` significa "anulada antes de enviarse: el hecho
   que la motivó dejó de ser relevante", y solo se alcanza desde `PENDING`.
   Descartar es lo contrario: el hecho sigue siendo relevante y lo que se abandona
   es el intento de entrega. Mezclarlos borraría la distinción entre "no hacía
   falta enviarlo" y "no conseguimos enviarlo y renunciamos", que es justamente la
   información que da valor a conservar la fila.

   Descartar una notificación **no la oculta**: como toda notificación fallida,
   sigue visible en la aplicación para su destinatario.

3. **La supresión es lógica y el motivo va a auditoría.**
   La fila se conserva con su `last_error` intacto y pasa a `DISCARDED`, de modo
   que deja de contar como incidencia pendiente sin perder la evidencia. El motivo
   —obligatorio— y el actor quedan en `audit_event`, no en columnas nuevas:
   añadir `discard_reason`/`discarded_at` a `outbox_message` obligaría a extender
   el record `OutboxMessage` y con él su mapper, su entidad y una decena de puntos
   de construcción, para un dato que el panel no muestra, porque los descartados
   desaparecen de la lista. Si algún día hace falta una pantalla de descartados
   con su motivo, se añadirá la columna entonces.

4. **Las transiciones manuales filtran por estado en la propia sentencia.**
   `requeueFailed`/`discardFailed` llevan `AND status = 'FAILED'` y devuelven si
   afectaron a alguna fila. `markRetry` escribía incondicionalmente: entre la
   comprobación del caso de uso y la escritura, otro administrador puede haber
   reintentado el mensaje y el publicador haberlo reclamado, y devolverlo entonces
   a `PENDING` lo publicaría dos veces. Perder esa carrera se responde con 409.

5. **Colección REST propia** bajo `/api/v1/platform/queues/{queue}/failed`, no una
   subruta de `system-status`: aquel es un informe de solo lectura, y colgarle
   mutaciones mezclaría dos recursos. Las acciones responden `204`, porque tras
   ejecutarlas el elemento ya no pertenece a la vista que las lanzó.

## Consecuencias

* (+) El panel deja de ser un mirador: quien ve una incidencia puede resolverla
  sin abrir una consola de base de datos.
* (+) Una cola futura gana listado, reintento y descarte implementando una
  interfaz, sin tocar el controlador ni el caso de uso.
* (+) Toda intervención queda auditada con actor, cola, tenant afectado y motivo.
* (−) Los `DISCARDED` se acumulan sin techo: el archivador solo purga `PUBLISHED`.
  Es el precio de conservar la traza; si llega a molestar, hará falta una política
  de retención propia y su ADR.
* (−) El listado es cross-tenant y solo lo protege el rol. Por eso los métodos de
  repositorio sin `tenantId` van documentados como excepción de plataforma y la
  prueba de seguridad del controlador no es opcional: `RepositoryTenantConventionTest`
  solo inspecciona `UserRepository` y no avisaría.
* (−) `lastError` puede contener trazas con datos sensibles. Se trunca en el
  mapper REST y no se exponen ni el payload del evento ni el cuerpo o el correo
  del destinatario.

## Alternativas descartadas

* **Borrado físico del elemento descartado**: es la opción más simple, pero
  destruye la única evidencia de qué falló y por qué se abandonó, que es
  precisamente lo que se quiere conservar.
* **Reutilizar `CANCELLED` en notificaciones**: evitaría la migración del CHECK,
  a cambio de perder la distinción entre anular y renunciar (ver decisión 2).
* **Un controlador de mantenimiento por módulo**: ver decisión 1.
* **Registrar los errores de aplicación en una tabla nueva**: resolvería otra
  pregunta —qué excepciones lanza el backend—, no la que motiva este panel, que es
  qué trabajo quedó atascado y nadie está mirando.
