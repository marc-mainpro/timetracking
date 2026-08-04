# ADR-0012: Envío de correo mediante puerto, fuera de la transacción de negocio

* Estado: accepted
* Fecha: 2026-08-04

## Contexto y problema

Tres funcionalidades de la V2 necesitan alcanzar al usuario fuera de la sesión
HTTP: la verificación de correo del alta pública (RF-REG-004), la recuperación de
contraseña (RF-USR-006) y las notificaciones (RF-NOT-002). Hasta ahora el
proyecto no enviaba correo: no había ni dependencia ni servicio SMTP.

La decisión relevante no es *qué librería usar* sino **dónde ocurre el envío**.
Enviar dentro del caso de uso, en la misma transacción que el cambio de negocio,
tiene dos consecuencias malas: un SMTP lento mantiene abierta una transacción de
base de datos, y un fallo de entrega provoca el rollback de un cambio de negocio
que ya era válido (el usuario quedaría sin contraseña restablecida porque el
servidor de correo estaba caído).

## Decisión

1. **Puerto `EmailSender` en `notification.application`**, con
   `EmailMessage(to, subject, body)` ya renderizado. La elección de plantilla e
   idioma se resuelve antes; el adaptador no conoce el dominio.

2. **El envío nunca forma parte de la transacción de negocio.** El caso de uso
   publica su evento de dominio, este viaja por el Outbox (ADR-0005) y es el
   consumidor interno quien invoca el puerto, con los reintentos y la
   idempotencia que el Outbox ya garantiza. Esto reutiliza la infraestructura
   existente en vez de inventar una cola de correo.

3. **`mail.enabled=false` por defecto.** Con el flag apagado el contenedor
   levanta un `EmailSender` que solo registra destinatario y asunto. Ni los tests
   ni un arranque local sin servidor de correo intentan abrir una conexión SMTP.
   Con `mail.enabled=true` se activa `SmtpEmailSender`.

4. **`mailpit` como servidor SMTP de desarrollo**, en `docker-compose.yml`,
   con interfaz web en el puerto 8025.

5. **Nunca se registra el cuerpo del mensaje** (RS-014): los correos de
   verificación y recuperación llevan tokens de un solo uso. Solo se registran
   destinatario y asunto.

6. **`EmailDeliveryException` no hereda de `DomainException`.** No es una regla de
   negocio violada por el usuario sino un fallo de infraestructura, y nunca debe
   traducirse a un 4xx ni llegar al borde REST: el envío ocurre fuera de la
   petición.

## Consecuencias

* (+) Un SMTP caído o lento no afecta a la latencia ni a la integridad de las
  operaciones de negocio.
* (+) Los reintentos, el backoff y la idempotencia son los del Outbox, ya
  probados; no hay una segunda infraestructura de reintento que mantener.
* (+) Los tests corren sin servidor de correo y sin mocks de SMTP.
* (−) El correo es asíncrono: entre la acción del usuario y la llegada del mensaje
  media al menos un ciclo de polling del Outbox (5 s por defecto). Aceptable para
  verificación y recuperación.
* (−) Con el adaptador por defecto los tokens se pierden: para probar esos flujos
  hay que capturar los mensajes con un doble de test o levantar mailpit.
* (−) `mailpit` no debe llegar a producción; el despliegue productivo tiene que
  apuntar `MAIL_HOST` a un SMTP real.

## Alternativas descartadas

* **Enviar síncronamente dentro del caso de uso**: acopla la latencia y la
  atomicidad del negocio a un tercero de red, que es el problema que motiva este
  ADR.
* **`@TransactionalEventListener(AFTER_COMMIT)`**: resuelve la atomicidad pero no
  la durabilidad. Si el proceso muere entre el commit y el envío, el correo se
  pierde sin rastro. El Outbox sí lo persiste.
* **Cola dedicada de correo con su propia tabla y su propio job**: duplicaría el
  Outbox, que ya hace exactamente eso.
* **Broker externo (RabbitMQ/Kafka)**: fuera del alcance de la V2 por RC-002 y
  RC-003.
