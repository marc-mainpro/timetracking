# ADR-0018: Destinatarios por rol y política de canal de notificación

* Estado: accepted
* Fecha: 2026-08-13

## Contexto y problema

Hasta la épica T170 el sistema notificaba **a una sola clase de usuario**: el
empleado, y solo sobre cinco hechos que le afectaban personalmente. Ni el
`TENANT_ADMIN` ni el `PLATFORM_ADMIN` recibían ningún aviso, de modo que las
decisiones que dependen de ellos —revisar una corrección, resolver una ausencia,
revisar un alta de organización— solo avanzaban si alguien entraba a mirar por
iniciativa propia. El caso más caro era el alta: una solicitud legítima podía
quedarse indefinidamente en `PENDING_REVIEW` sin que nadie se enterase.

Completar el catálogo para los tres roles choca con dos supuestos que estaban
incrustados en la infraestructura de notificación, y ninguno es un detalle de
implementación:

1. **Un evento tiene exactamente un destinatario, y está en su payload.** La
   plantilla de `NotificationEventListener` declaraba el nombre de un campo
   (`employeeId`) del que salía el usuario. No había forma de expresar «todos los
   administradores activos de este tenant», que es justo lo que un aviso de
   administrador necesita.
2. **Toda notificación se envía por correo.** `NotificationSender` entregaba
   cualquiera que estuviese `PENDING`. No existía el concepto de aviso solo
   in-app, y sin él, en un tenant con cincuenta empleados, las anomalías diarias
   de jornada convertirían el buzón del administrador en ruido.

## Decisión

### 1. El destinatario es una estrategia, no un campo del payload

La plantilla declara **cómo** se resuelven sus destinatarios
(`notification.application.NotificationRecipients`), no quién es:

* `FromPayload(campo)` — un usuario nombrado por el evento, el comportamiento
  anterior;
* `TenantRole(rol, excluido)` — todos los usuarios activos con ese rol en el
  tenant del evento;
* `PlatformAdmins()` — todos los `PLATFORM_ADMIN` activos.

Los dos últimos preguntan por un puerto nuevo, `RoleRecipientQuery`, declarado en
`notification.application` e implementado en `identity.infrastructure`. Es el
mismo patrón que ya seguía el puerto del correo del destinatario —hoy
`UserDirectoryQuery`, que al redactar los textos pasó a resolver también el
nombre—: la dependencia apunta del que sabe hacia el que pregunta, y
`notification` sigue sin conocer cómo se almacenan los usuarios.

Tres consecuencias que son decisiones, no efectos colaterales:

* **Un evento puede tener varias plantillas.** La anomalía de jornada notifica al
  empleado con correo y a sus administradores sin él, con tipos distintos
  (`WORKDAY_ANOMALY_DETECTED` y `TEAM_WORKDAY_ANOMALY`), porque son avisos
  distintos aunque el hecho sea el mismo.
* **La reserva de idempotencia sigue siendo una por `(eventId, consumidor)`.**
  Todas las notificaciones del evento se crean bajo esa única reserva y en la
  misma transacción: o están todas o no está ninguna.
* **Los destinatarios se resuelven antes de reservar.** Si no hay a quien avisar
  —un tenant sin administradores activos— no se ha hecho nada, así que consumir
  la reserva impediría notificar cuando el evento se reentregase existiendo ya un
  destinatario. Cero destinatarios nunca falla ni bloquea el mensaje del outbox.
* **Solo destinatarios activos.** Un administrador desactivado no debe recibir
  avisos operativos de una organización en la que ya no puede entrar.
* **Al actor del hecho no se le avisa por la vía del rol.** Quien pide una
  corrección o una ausencia ya sabe que la ha pedido, aunque además sea
  administrador; la estrategia lo excluye por el campo del payload que lo nombra.

### 2. El canal se persiste en la fila, no se resuelve en memoria

`notification.email_required` (BOOLEAN NOT NULL DEFAULT TRUE) lo fija el tipo al
crear la notificación, y `findPendingForDelivery` filtra por él en SQL; el índice
parcial de la cola se recreó incluyéndolo.

La alternativa evidente —consultar el tipo dentro de `isDeliverable()`— **es
incorrecta, no solo menos elegante**: la consulta de la cola filtra por estado en
SQL, así que las notificaciones sin correo se recuperarían cada 30 segundos, el
emisor las descartaría sin marcarlas y quedarían envenenando la cola
indefinidamente, desplazando del lote a las que sí hay que enviar.

`TEAM_WORKDAY_ANOMALY` es hoy el único tipo con `email_required = false`, y es la
excepción que justifica la columna. `ACCOUNT_DEACTIVATED` es el caso simétrico:
el correo es el **único** canal útil, porque el usuario ya no puede iniciar
sesión para ver el aviso in-app; la fila in-app se crea igualmente, sin coste, y
queda como histórico si la cuenta se reactiva.

### 3. El enlace es una ruta relativa, y la base es configuración

`notification.action_path` guarda una ruta del frontend (`/admin/corrections`,
`/platform/registrations`…), nunca una URL absoluta, y el agregado rechaza
cualquier valor que no empiece por `/`. La base la pone el compositor de correo
desde `notification.email.app-base-url`, de modo que una notificación no puede
acabar apuntando a otro dominio y la misma fila sirve en todos los entornos.
Sirve a dos consumidores: el correo, que forma la URL absoluta, y el frontend,
que navega al pulsar el aviso.

## Consecuencias

* (+) Añadir un aviso nuevo vuelve a ser una entrada en la tabla de plantillas,
  incluso para roles cuyo destinatario no está en el evento.
* (+) El alta de organización deja de depender de que alguien mire la bandeja.
* (+) La columna `email_required` deja abiertas las preferencias por usuario sin
  comprometer el diseño: pasaría de constante por tipo a valor calculado por
  usuario, sin tocar la consulta de la cola.
* (−) El fan-out amplifica la carga de la cola de envío: un tenant con muchos
  administradores multiplica las filas de un mismo hecho. Lo mitiga que el emisor
  ya procesa por lotes y cada notificación va en su propia transacción; hay que
  vigilar `notification.pending` en el panel de estado.
* (−) Resolver destinatarios antes de reservar hace que una reentrega de un
  evento ya procesado repita la consulta por rol. Es una consulta indexada y
  barata, y a cambio se conserva la propiedad de que cero destinatarios no
  consume la reserva.
* (−) `SYSTEM_QUEUE_STUCK` (T170-08) recuerda en memoria si ya avisó de la
  condición actual. Un reinicio vuelve a avisar de un atasco que siguiera activo,
  que es preferible a perder el aviso, pero no es antirrepetición durable ni
  coordinada entre instancias.
* (−) `absence.absence-requested.v1` revierte una decisión previa documentada.
  Queda explicado en el mapeador y en el catálogo: la premisa original era «sin
  consumidor», y esta épica crea el consumidor.

## Alternativas descartadas

* **Resolver el canal en memoria dentro de `isDeliverable()`**: envenena la cola
  de envío, como se explica arriba. Es el motivo real de que la columna exista.
* **Guardar el rol destinatario en la notificación y expandirlo al leer**:
  dejaría la bandeja de cada usuario dependiendo de su rol *actual*, no del que
  tenía cuando ocurrió el hecho, y rompería el contador de no leídos al cambiar
  un rol. La notificación es una foto del momento, igual que el correo
  desnormalizado.
* **Un resumen periódico agrupado para administradores**: mitigaría la inundación
  del buzón sin necesidad de canal por tipo, pero requiere un planificador y
  decidir la ventana de agrupación sin datos reales de volumen. Se pospone; el
  punto de decisión es si `TEAM_WORKDAY_ANOMALY` acaba necesitando correo.
* **Preferencias de notificación por usuario**: es la solución completa al
  problema del ruido, y es también una funcionalidad con su propia UI, su
  migración y sus casos de uso. Fijar el canal en el código resuelve hoy el caso
  que duele sin cerrar esa puerta.
* **Guardar la URL absoluta en `action_path`**: obligaría a conocer el dominio
  del frontend en el momento de crear la notificación —dentro del consumidor del
  outbox, sin petición HTTP de la que deducirlo— y ataría las filas a un entorno.
