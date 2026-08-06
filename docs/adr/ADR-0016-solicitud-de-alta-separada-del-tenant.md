# ADR-0016: La solicitud de alta pública es un agregado separado del tenant

* Estado: accepted
* Fecha: 2026-08-04

## Contexto y problema

Hasta la V2 el alta pública era `POST /api/v1/auth/register`: una única llamada
que ejecutaba `RegisterTenantUseCase`, el cual invocaba `Tenant.register(...)` y
dejaba el tenant en `ACTIVE` junto con su primer `TENANT_ADMIN`. Es decir,
cualquiera con acceso al endpoint —protegido solo por el flag
`registration.public.enabled` y por rate limiting por IP— podía crear una
organización plenamente operativa sin demostrar siquiera que controlaba el
correo que declaraba.

Eso contradice tres cosas a la vez:

* el diseño §7.3, que describe el flujo
  `solicitud → verificación → tenant en PENDING → revisión o activación`;
* el criterio T53-03, «no crear tenant activo directamente»;
* RF-REG-004 y RF-REG-005, que exigen verificación de correo y respuestas que no
  revelen si una dirección ya está registrada.

El problema de fondo es de modelado: **una solicitud de alta y un tenant no son
la misma cosa en distintos estados**. Una solicitud puede caducar, rechazarse o
duplicarse; un tenant no. Una solicitud tiene una contraseña pendiente de
convertirse en usuario; un tenant ya tiene usuarios. Forzarlos en un mismo
agregado obligaría a inventar estados de tenant (`REJECTED`, `EXPIRED`) que no
tienen sentido para una organización real, y a persistir filas de `tenant` que
nunca serán una organización.

## Decisión

1. **`TenantRegistration` es un agregado propio** en `tenant.domain`, con su
   tabla `tenant_registration` (V12) y su propio ciclo de vida:
   `PENDING_EMAIL_VERIFICATION → PENDING_REVIEW → APPROVED → CONSUMED`, con
   salidas a `EXPIRED` y `REJECTED`. Las transiciones válidas e inválidas viven
   en el agregado; ningún controlador comprueba estados.

2. **El registro público no crea tenants.** `POST /api/v1/public/tenant-registrations`
   solo persiste la solicitud. El tenant se crea al aprobar
   (`ApproveTenantRegistrationUseCase`) mediante `Tenant.requestRegistration(...)`,
   es decir **en estado `PENDING`**. Activarlo sigue siendo una decisión aparte
   del `PLATFORM_ADMIN` (RF-TEN-005). Aprobar la solicitud significa «esta
   organización parece legítima», no «esta organización puede operar».

3. **La aprobación es transaccional e idempotente.** Solicitud → `APPROVED` →
   tenant + propietario `TENANT_ADMIN` → solicitud `CONSUMED`, todo en la misma
   transacción. Una segunda aprobación encuentra `CONSUMED` y devuelve el tenant
   ya creado en vez de crear otro.

4. **Del token de verificación solo se persiste el hash** (SHA-256 hex). El
   valor en claro existe únicamente en memoria y dentro del correo. Verificar
   consume el token: el hash se borra, de modo que el enlace es de un solo uso
   aunque se reenvíe. La caducidad y el límite de reenvíos son reglas del
   agregado.

5. **Respuestas anti-enumeración (RF-REG-005).** Solicitud y reenvío responden
   siempre `202 Accepted` con el mismo cuerpo genérico, y el caso de uso no
   lanza excepciones de negocio: correo ya registrado, solicitud duplicada y
   cuota de abuso agotada son indistinguibles desde fuera. Los tres caminos
   hashean la contraseña antes de decidir, para que tampoco el tiempo de
   respuesta delate el caso. La verificación sí devuelve error, pero uno solo
   (`INVALID_VERIFICATION_TOKEN`) para «no existe», «caducado» y «ya usado»:
   distinguirlos convertiría el endpoint en un oráculo de tokens.

6. **La IP se guarda como huella con pepper**, no en claro. El espacio IPv4 es
   pequeño: un SHA-256 sin sal se invierte por fuerza bruta, así que sin el
   pepper (`registration.ip-hash-pepper`) guardar el hash equivaldría a guardar
   la IP.

7. **`POST /api/v1/auth/register` se ha retirado.** *(Actualizado: al cerrar
   esta ADR el endpoint quedó `@Deprecated` pero operativo, porque
   `TestTenantFactory` —y con ella la batería de integración de todos los
   módulos— arrancaba sus tenants por ahí y migrarla era un cambio transversal
   ajeno a la épica.)* La deuda está saldada: el helper invoca ahora
   `RegisterTenantUseCase` directamente, y el caso de uso sigue vivo porque lo
   usan la creación desde plataforma y la aprobación de solicitudes. La única
   vía pública de alta es el flujo de solicitud, verificación y aprobación
   descrito arriba.

8. **El correo sale por el Outbox, no por el caso de uso** (ADR-0012). El
   agregado emite `TenantRegistrationVerificationRequested`, el mapper lo
   traduce a `tenant.registration-verification-requested.v1` y
   `TenantRegistrationEmailListener` (en `tenant.infrastructure`) invoca el
   puerto `EmailSender`.

## Consecuencias

* (+) Una avalancha de altas fraudulentas ya no genera tenants: genera filas de
  solicitud que caducan solas y que ningún usuario puede usar para entrar.
* (+) El `PLATFORM_ADMIN` tiene una bandeja explícita
  (`GET /api/v1/platform/registrations?status=PENDING_REVIEW`) en vez de
  descubrir tenants nuevos ya operativos en el listado.
* (+) El estado terminal `CONSUMED` con `created_tenant_id` deja trazabilidad
  permanente entre la solicitud y el tenant que produjo.
* (−) El alta pasa de un paso a tres (solicitar, verificar, esperar
  aprobación). Es deliberado, pero encarece el alta autoservicio.
* (−) **El token en claro viaja dentro del payload del evento de integración**,
  y por tanto se persiste en `outbox_message.payload` hasta que el archivador lo
  purga. Es la contrapartida de sacar el envío de la transacción (ADR-0012).
  Mitigaciones: el publicador registra solo el sobre del evento, nunca el
  payload; el token caduca (24 h por defecto) mucho antes de la retención del
  outbox (30 días); y es de un solo uso, de modo que un token ya consumido no
  sirve aunque se lea la fila. Aun así, quien pueda leer `outbox_message` puede
  secuestrar una solicitud no verificada. Si esto deja de ser aceptable, la
  salida es cifrar el payload del outbox o darle al mensaje de correo su propia
  tabla con retención corta; ambas exigen una ADR nueva.
* (−) `TenantRegistrationRepository` **no lleva `tenantId` como primer
  parámetro**, rompiendo la convención de ADR-0002. No hay alternativa: la
  solicitud es anterior al tenant. El aislamiento aquí lo da la autorización por
  rol (`PLATFORM_ADMIN`) y el hecho de que ningún endpoint de tenant exponga la
  tabla; queda documentado en el propio puerto.
* (−) Un fallo de envío en `TenantRegistrationEmailListener` no se reintenta: el
  publicador registra el fallo del listener pero marca el mensaje `PUBLISHED`
  igualmente (comportamiento actual de `LoggingIntegrationEventPublisher`). El
  usuario tiene el reenvío manual como red de seguridad. Corregirlo exige tocar
  el contrato del publicador, que pertenece al módulo `outbox`.

## Alternativas descartadas

* **Añadir estados `PENDING_VERIFICATION`/`REJECTED` al agregado `Tenant`**:
  contamina el ciclo de vida de una organización real con estados de un trámite,
  y deja filas en `tenant` que nunca serán organizaciones. El listado de
  plataforma tendría que filtrarlas siempre.
* **Crear el tenant en `PENDING` ya en la solicitud y borrarlo si se rechaza**:
  convierte un flujo de negocio en un borrado, con todo lo que arrastra
  (claves foráneas, auditoría de algo que nunca existió) y hace que un bot pueda
  llenar la tabla `tenant`.
* **Guardar el token en claro** para poder reenviar el mismo enlace: convierte
  la tabla en un almacén de credenciales de un solo uso. Reenviar un token nuevo
  cuesta lo mismo y no tiene ese problema.
* **Verificar por GET con el token en la URL**: el token acabaría en logs de
  acceso, en el historial del navegador y en cabeceras `Referer`. El enlace del
  correo apunta al frontend, que hace el `POST`.
* **Devolver 409 cuando el correo ya existe**, como hace el alta manual de
  plataforma: sería la respuesta honesta, pero convierte el endpoint público en
  un verificador de cuentas (RF-REG-005). En el alta de plataforma sí se
  devuelve, porque quien la usa ya está autenticado como `PLATFORM_ADMIN`.
