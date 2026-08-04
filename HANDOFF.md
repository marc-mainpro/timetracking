# HANDOFF — Agente A1, Ola 1, épica T53 (registro público seguro)

Rama: `worktree-agent-a9e3d82abd86a8c50`.

## 0. Antes de nada: el worktree partía de una base equivocada

El worktree se creó desde `6370dd2` (dos PR de arreglos de concurrencia), que
**no contiene la base V2 de la Ola 1**: sin `ADR-0010/0011/0012`, sin
`docs/agents/reservas-v2.md`, sin los documentos de requisitos/diseño/tareas de
la V2, sin `PublicEndpointsContributor`, sin `TenantPublicEndpoints`, sin el
puerto `EmailSender` y sin `Tenant.requestRegistration(...)`. Es decir, sin nada
de lo que el encargo daba por existente.

He fusionado `021acd0` (`feat/v2-ola1-registro-seguridad-calendarios`) en el
worktree en vez de reescribir la base, para no perder los dos commits de
arreglos que solo existían aquí. Dos consecuencias que hay que revisar al
mergear:

1. **Conflicto resuelto a mano en
   `identity/application/AssignRoleUseCase.java`**: he combinado las dos
   versiones (el `Role.tenantAssignableRoles(...)` de la base V2 **y** el
   `lockActiveAdmins(...)` del arreglo de concurrencia). No es código mío, pero
   la resolución es mía.
2. **`identity/infrastructure/PlatformAdminBootstrapTest.java`**: su
   `InMemoryUserRepository` no compilaba tras la fusión porque le faltaba
   `lockActiveAdmins(UUID)`. He añadido la implementación vacía.

Si el agente principal prefiere una base distinta, este merge es lo primero que
hay que rehacer.

## 1. Líneas que necesito en ficheros PROHIBIDOS

### `backend/src/main/resources/application.yml`

`registration.public.enabled` está ahora **duplicado**: sigue en
`application.yml` y también está en mi `config/registration.yml`. Ambos resuelven
la misma expresión `${PUBLIC_REGISTRATION_ENABLED:false}`, así que el
comportamiento es idéntico gane quien gane la precedencia, y por eso no he
tocado nada. Aun así conviene **borrar el bloque de `application.yml`** para que
la configuración del registro viva en un único sitio (ADR-0011):

```yaml
# BORRAR de application.yml (ya está en config/registration.yml):
registration:
  public:
    enabled: ${PUBLIC_REGISTRATION_ENABLED:false}
```

### `backend/src/main/java/.../shared/infrastructure/security/RateLimitFilter.java`

No es «prohibido» pero es de A2 (T30-03), así que no lo he tocado. Mis endpoints
públicos **no pasan por el filtro de rate limiting**; la protección anti-abuso
que sí implemento es de dominio (cuotas por IP y por correo dentro de una
ventana, RF-REG-003). Cuando A2 rehaga el filtro, debería añadir:

```java
private static final Set<String> PROTECTED_PATHS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/public/tenant-registrations",
        "/api/v1/public/tenant-registrations/verify-email",
        "/api/v1/public/tenant-registrations/resend-verification");
```

### `docker-compose.yml`, `docker-compose.prod.yml`, `.env.example`

Variables de entorno nuevas (todas opcionales, con valor por defecto seguro):

```
PUBLIC_REGISTRATION_ENABLED=false
REGISTRATION_TOKEN_TTL=PT24H
REGISTRATION_MAX_RESENDS=3
REGISTRATION_THROTTLE_WINDOW=PT1H
REGISTRATION_MAX_PER_IP=5
REGISTRATION_MAX_PER_EMAIL=3
REGISTRATION_VERIFICATION_URL=http://localhost:4200/registro/verificar?token=%s
REGISTRATION_IP_HASH_PEPPER=            # secreto; vacío = aleatorio por arranque
```

Dos avisos operativos:

* `REGISTRATION_VERIFICATION_URL` debe apuntar al **frontend**, no al backend, y
  contener exactamente un `%s`. En producción hay que cambiar el `localhost:4200`.
* `REGISTRATION_IP_HASH_PEPPER` debe fijarse en producción. Vacío se genera uno
  aleatorio en cada arranque, lo que es seguro pero hace que las cuotas por IP no
  sobrevivan a un reinicio ni se compartan entre instancias.

### `docs/api/openapi.yaml`

Endpoints nuevos a reexportar desde `/v3/api-docs.yaml`. Ya llevan `@Tag` y
`@Operation`:

* `POST /api/v1/public/tenant-registrations` → 202
* `POST /api/v1/public/tenant-registrations/verify-email` → 200
* `POST /api/v1/public/tenant-registrations/resend-verification` → 202
* `GET /api/v1/platform/registrations` → 200
* `POST /api/v1/platform/registrations/{registrationId}/approve` → 200
* `POST /api/v1/platform/registrations/{registrationId}/reject` → 200

`AuthRegisterController` está marcado `@Deprecated`; la reexportación debería
reflejarlo.

## 2. Ficheros compartidos que he tocado (solo añadiendo)

* `frontend/src/app/app.routes.ts`: ruta `registro` insertada antes de `reports`
  (orden alfabético local).
* `frontend/src/app/app.component.html`: enlace «Solicitudes» en el bloque
  `showPlatformLinks()` y enlace «Alta de organización» en el nav de invitado.
* `frontend/src/app/features/platform/platform.routes.ts`: ruta `registrations`.
* `docs/traceability/requirements-matrix.md`: dos secciones nuevas; he borrado la
  viñeta «T53 pendiente» de la sección *Pendiente*, que ya no lo está.
* `docs/integration/event-catalog.md`: cinco secciones `###` al final de «Tipos
  de evento».
* `docs/acceptance-checklist.md`: sección «Registro público controlado (T53)».
* `backend/src/test/.../shared/infrastructure/security/RouteAuthorizationIntegrationTest.java`:
  mis tres rutas públicas añadidas a `PUBLIC_ROUTES`. **Es obligatorio**: sin
  ellas ese test falla, porque comprueba que toda ruta no listada exige 401.

## 3. Reservas usadas

* **Migración: solo `V12__tenant_registration.sql`.** V13 queda libre.
* **ADR: `ADR-0013-solicitud-de-alta-separada-del-tenant.md`.** 0014–0017 libres
  para el resto de la Ola 1.
* No he tocado `identity.domain.User`, `UserStatus` ni `AuthenticateUserUseCase`
  (territorio de A2), salvo la resolución del conflicto de merge en
  `AssignRoleUseCase` y el arreglo de compilación en `PlatformAdminBootstrapTest`
  descritos en §0.

## 4. Eventos de integración publicados

Todos con `tenantId` = tenant de plataforma (`00000000-...-0001`) y
`aggregateType` = `TenantRegistration`. Documentados en el catálogo.

| `eventType` | Cuándo | Quién debería consumirlo |
|---|---|---|
| `tenant.registration-requested.v1` | se envía el formulario público | métricas / antifraude |
| `tenant.registration-verification-requested.v1` | alta y cada reenvío | `TenantRegistrationEmailListener` (ya implementado) |
| `tenant.registration-email-verified.v1` | el solicitante confirma el correo | aviso a plataforma de que hay bandeja |
| `tenant.registration-approved.v1` | `PLATFORM_ADMIN` aprueba | provisión de recursos externos del tenant nuevo |
| `tenant.registration-rejected.v1` | `PLATFORM_ADMIN` rechaza | antifraude / métricas |

## 5. Riesgos y decisiones asumidas

1. **El token de verificación en claro se persiste en `outbox_message.payload`.**
   Es la consecuencia directa de ADR-0012 (el envío va por el Outbox) combinada
   con que el consumidor necesita el token para construir el enlace. Mitigado
   (el publicador no registra el payload; el token caduca en 24 h y es de un solo
   uso), pero **quien pueda leer `outbox_message` puede secuestrar una solicitud
   no verificada**. Está documentado en las consecuencias de ADR-0013. Si se
   considera inaceptable, hay que cifrar el payload del outbox o darle al correo
   su propia tabla con retención corta; ambas cosas exigen una ADR nueva y tocan
   el módulo `outbox`, que no es mío.

2. **Un fallo de envío del correo no se reintenta.**
   `LoggingIntegrationEventPublisher` captura las excepciones de los
   `IntegrationEventListener`, las registra y marca el mensaje `PUBLISHED`
   igualmente. Así que si el SMTP falla, el correo se pierde: ADR-0012 promete
   los reintentos del Outbox, pero el enganche actual no los da. La red de
   seguridad es el reenvío manual (`/resend-verification`). Arreglarlo de verdad
   exige cambiar el contrato del publicador (módulo `outbox`).

3. **`POST /api/v1/auth/register` sigue vivo y sigue creando tenants `ACTIVE`.**
   Lo he marcado `@Deprecated` pero no lo he retirado: `TestTenantFactory` lo usa
   para arrancar tenants y de ella dependen 12 suites de integración de todos los
   módulos. Retirarlo es un cambio transversal que habría chocado con los otros
   agentes de la ola. **Deuda concreta**: migrar `TestTenantFactory` a crear
   tenants por `POST /api/v1/platform/tenants` (o directamente por repositorio) y
   borrar `AuthRegisterController`, `RegisterTenantRequest/Response` y la ruta
   pública correspondiente. Mientras tanto, la contradicción con T53-03 está
   contenida por el flag: en producción está apagado.

4. **`TenantRegistrationRepository` no lleva `tenantId` como primer parámetro.**
   No puede: la solicitud existe antes que el tenant. `RepositoryTenantConventionTest`
   solo inspecciona `UserRepository`, así que no falla, pero es una excepción
   real a ADR-0002. Documentada en el javadoc del puerto y en ADR-0013.

5. **La auditoría anónima no usa `AuditRecorder`.** `AuditRecorder` resuelve
   tenant y actor desde el JWT, y en el alta pública no hay JWT. He añadido el
   puerto `RegistrationAuditTrail` (en `tenant.application`) con
   `AnonymousRegistrationAuditTrail` (en `tenant.infrastructure`), que escribe en
   la misma tabla con el tenant de plataforma y `actorUserId = null`. Si otro
   agente necesita lo mismo (recuperación de contraseña, por ejemplo), quizá
   convenga promover esto a `audit` como una segunda operación del puerto en vez
   de duplicarlo.

6. **Cuotas anti-abuso en memoria de base de datos, no en el filtro.** Cuento
   filas de `tenant_registration` por `ip_hash`/`email` dentro de la ventana. Es
   correcto y sobrevive a reinicios, pero es una consulta por solicitud. Con el
   flag apagado por defecto no es un problema de carga; si el alta pública se
   abre de verdad, conviene coordinar con el rate limiting de A2.

7. **`registration.public.enabled` gobierna los cinco endpoints públicos** (los
   tres míos y el heredado). No he separado banderas para no multiplicar
   configuración; si se quiere abrir el flujo V2 manteniendo cerrado el heredado,
   hace falta una bandera nueva.

## 6. Verificación

* `cd backend && mvn -B verify` → **BUILD SUCCESS** (incluye ArchUnit y los
  gates de JaCoCo 90 % dominio / 80 % aplicación).
* `cd frontend && npx ng lint` → *All files pass linting*.
* `cd frontend && npm run test:coverage` → 101 specs en verde; cobertura
  statements 74,3 % / branches 66,4 % / functions 64,9 % / lines 75,5 %, por
  encima de los umbrales de `karma.conf.js`.
* `cd frontend && npx ng build` → bundle generado, con `registration-routes`
  como chunk perezoso propio.

Nota: `npm ci` era necesario en este worktree (no había `node_modules`).
