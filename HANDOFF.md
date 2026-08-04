# HANDOFF — Agente A2, Ola 1 (T30-03 rate limiting, T30-04 bloqueo de cuentas)

Rama: `worktree-agent-ac20843cf664aeedf`, sobre
`feat/v2-ola1-registro-seguridad-calendarios` (021acd0).

Estado: `mvn -B verify` verde (incluye ArchUnit y los dos `jacoco:check`);
`npx ng lint`, `npm run test:coverage` (78 specs) y `npx ng build` verdes.

**Aviso de partida:** el worktree se creó a partir de `main`, que no contiene
los documentos de la V2 ni la Ola 0. Se reajustó con `git reset --hard` a
`feat/v2-ola1-registro-seguridad-calendarios` antes de empezar; no se perdió
trabajo (el árbol estaba limpio). Conviene comprobar la base de partida de los
demás worktrees de la ola.

---

## 1. Ficheros prohibidos — cambios que necesito que apliquéis

### `application.yml` (una eliminación, opcional pero recomendada)

El bloque `auth.rate-limit` sigue en `application.yml` y **se usa**: es el
límite por defecto que heredan las reglas sin `capacity`/`window` propios. No
hay que tocarlo.

Lo único prescindible es documentarlo mejor. Sugerencia, si queréis aplicarla:

```yaml
auth:
  rate-limit:
    # Límite por defecto. Las reglas por endpoint viven en
    # config/account-lockout.yml (auth.rate-limit.endpoints[]) y heredan
    # estos valores cuando no declaran los suyos. Ver ADR-0013.
    capacity: 10
    window: PT1M
```

### `.env.example` / `docker-compose*.yml` (opcional)

Tres variables nuevas, todas con valor por defecto en el código, así que **no
son obligatorias** para arrancar. Si queréis exponerlas:

```
AUTH_LOCKOUT_THRESHOLD=5
AUTH_LOCKOUT_DURATION=PT15M
AUTH_LOCKOUT_FAILURE_WINDOW=PT30M
```

### `docs/api/openapi.yaml`

No he editado el YAML (es generado). El único cambio de contrato es que
`POST /api/v1/auth/login` puede devolver **401 con `errorCode: ACCOUNT_LOCKED`**,
además de los ya existentes. Tenedlo en cuenta al reexportar al final de la ola.

### `SecurityConfig`

**Sin cambios.** No hace falta ningún filtro nuevo ni ninguna alteración del
orden: `RateLimitFilter` sigue siendo el mismo bean en la misma posición, solo
cambia cómo decide a qué rutas aplica.

### `backend/pom.xml`, lockfiles, `.github/workflows/ci.yml`

**Sin cambios.** No he añadido ninguna dependencia (bucket4j y Micrometer ya
estaban).

---

## 2. Configuración

Fichero nuevo: `backend/src/main/resources/config/account-lockout.yml`, ya
declarado en el `spring.config.import` de `application.yml`. No he necesitado
ningún fichero de configuración adicional.

| Propiedad | Defecto | Qué hace |
|---|---|---|
| `auth.account-lockout.threshold` | `5` | Intentos fallidos consecutivos que bloquean la cuenta |
| `auth.account-lockout.lock-duration` | `PT15M` | Cuánto dura el bloqueo |
| `auth.account-lockout.failure-window` | `PT30M` | Ventana tras la que un fallo aislado deja de contar |
| `auth.rate-limit.endpoints[]` | ver fichero | Reglas por patrón: `method`, `pattern`, `capacity` y `window` opcionales |

`auth.rate-limit.capacity` y `auth.rate-limit.window` siguen en
`application.yml` y actúan como valor heredado.

Variables de entorno: `AUTH_LOCKOUT_THRESHOLD`, `AUTH_LOCKOUT_DURATION`,
`AUTH_LOCKOUT_FAILURE_WINDOW`. Puertos nuevos: ninguno.

---

## 3. Rutas que el rate limiting espera cubrir

Las reglas están declaradas **por patrón**, así que estos endpoints nacerán
protegidos aunque su autor no toque nada. Lo que sí necesito es que respetéis
el prefijo:

| Patrón configurado | Límite | Endpoints previstos | Agente |
|---|---|---|---|
| `POST /api/v1/auth/login` | 10 / min (heredado) | ya existe | — |
| `POST /api/v1/auth/register` | 10 / min (heredado) | ya existe | A1 (T53) |
| `POST /api/v1/auth/refresh` | 30 / min | ya existe | — |
| `POST /api/v1/auth/password/**` | 5 / 15 min | `password/forgot`, `password/reset` | B1 (T60, recuperación de contraseña) |
| `POST /api/v1/auth/verification/**` | 5 / 15 min | `verification/resend`, `verification/confirm` | A1 (T53, verificación de correo) |

**Petición a A1 y B1:** colgad vuestros endpoints de esos prefijos
(`/api/v1/auth/password/...` y `/api/v1/auth/verification/...`). Si preferís
otra ruta, decidlo y añado la regla; lo que no quiero es que un endpoint que
envía correos quede fuera del límite por una diferencia de nombre.

**Petición a A1 (registro):** `AuthRegisterController` no lo he tocado. El
límite de `POST /api/v1/auth/register` sigue vigente por patrón, así que no
tienes que hacer nada para conservarlo; solo evita cambiar esa ruta sin avisar.

`RateLimitFilterIntegrationTest` ya prueba los dos patrones futuros contra
rutas que todavía no existen.

---

## 4. Métricas expuestas (coordinación con A5, T140 observabilidad)

Micrometer, vía Actuator, siguiendo el patrón de `OutboxMetrics`:

| Métrica | Tipo | Etiquetas | Significado |
|---|---|---|---|
| `auth.login.failed` | contador | `reason` = `bad_credentials` \| `unknown_email` \| `locked` | Intentos de login rechazados |
| `auth.login.succeeded` | contador | — | Logins completados |
| `auth.accounts.locked` | contador | — | Bloqueos temporales aplicados |

Las tres series de `auth.login.failed` se preregistran a 0 en el arranque: una
alerta sobre una métrica ausente no se distingue de un sistema sano.

**Deliberadamente sin etiqueta de tenant, usuario ni IP.** Además de la
cardinalidad, las métricas no tienen control de acceso por tenant y una serie
por usuario sería otra vía de enumeración. Si necesitas desglose por tenant,
hablémoslo antes: la fuente correcta es `audit_event`, que sí está
tenant-scoped.

Sugerencia de alerta para T140: subida sostenida de `auth.accounts.locked` o
ratio alto de `auth.login.failed{reason="unknown_email"}` (enumeración en
curso).

---

## 5. Otros cambios en ficheros compartidos

* **`AuditRecorder` (`audit.application`)**: **añadida** una sobrecarga
  `record(tenantId, actorUserId, action, entityType, entityId, metadata)`. Es
  necesaria porque un login fallido debe auditarse justo cuando no hay
  principal autenticado del que `JpaAuditRecorder` pueda leer el tenant (el
  `TenantContext` actual lanza `IllegalStateException` en ese caso). La firma
  antigua no cambia.

  **Efecto colateral a tener en cuenta:** `AuditRecorder` deja de ser interfaz
  funcional. Adapté el único uso como lambda que había
  (`ApproveCorrectionRequestUseCaseAtomicityIntegrationTest`, ahora clase
  anónima). Si en vuestra rama habéis creado otro doble de test con lambda,
  os saltará al mergear: convertidlo en clase anónima.

* **`GlobalExceptionHandler`**: una línea, `ACCOUNT_LOCKED` añadido al conjunto
  de códigos que mapean a 401 en vez de 409.

* **`AuthenticateUserUseCase`**: dos parámetros nuevos en el constructor
  (`AccountLockoutService`, `AuthenticationMetrics`). Si alguien lo instancia a
  mano en un test, hay que actualizarlo.

* **`error-messages.service.ts`**: una entrada nueva (`ACCOUNT_LOCKED`);
  `RATE_LIMIT_EXCEEDED` ya existía. Sin cambios en rutas ni en el layout, así
  que sin conflicto con `app.routes.ts` ni `app.component.html`.

* Docs: fila nueva en la matriz de trazabilidad, sección nueva **al final** del
  modelo de amenazas, viñetas nuevas en el checklist de aceptación,
  `ADR-0013`. No he tocado nada ajeno ni reformateado tablas.

Migraciones usadas: **V14** (`account_lockout`). **V15 queda libre**, no me hizo
falta.

Eventos de integración publicados: **ninguno**. El bloqueo es un hecho de
seguridad interno y ningún otro módulo lo consume, así que no hay entrada nueva
en el catálogo de eventos. Si más adelante notificaciones (B4) quiere avisar al
usuario de un bloqueo, ese es el momento de crear el evento; queda apuntado.

---

## 6. Riesgos asumidos y decisiones que conviene revisar

1. **Rate limiting sigue en memoria (bucket4j).** No lo he cambiado: Redis
   sería una dependencia nueva y está prohibido. Con varias réplicas el límite
   efectivo se multiplica. Está aceptado en `owasp-review.md` y ampliado en la
   sección nueva del modelo de amenazas. La defensa que **sí** funciona en
   multi-instancia es el bloqueo por cuenta, porque está persistido.

2. **`ACCOUNT_LOCKED` solo se devuelve a quien acierta la contraseña.** Es la
   solución al conflicto entre «avisa al usuario» (RF-USR-008) y «no permitas
   enumerar» (RS-008). Si producto prefiere avisar siempre, es una decisión de
   negocio consciente y hay que cambiar el ADR-0013, no solo el código.

3. **Sin `@Version` en `account_lockout`.** Dos fallos simultáneos pueden perder
   un incremento: como mucho un intento extra. Preferible a que un login falle
   con `CONCURRENT_MODIFICATION`.

4. **`registerFailedAttempt` es `REQUIRES_NEW`.** Sin eso el contador se perdía
   con el rollback de `InvalidCredentialsException` y el bloqueo no saltaba
   nunca. Consume una segunda conexión del pool durante el login fallido; con
   el rate limiting delante el volumen está acotado, pero es algo a vigilar si
   alguien sube mucho las cuotas.

5. **Cada intento fallido escribe en `audit_event`.** El ritmo lo marca el
   atacante. Acotado por rate limiting y por el propio bloqueo, pero si B5
   (auditoría de tenant) añade retención o purga, esta es la tabla que más va a
   crecer.

6. **Enumeración por `USER_INACTIVE`, preexistente.** El login responde
   `USER_INACTIVE` antes de comprobar la contraseña, así que se puede
   distinguir un usuario desactivado de uno inexistente. No lo he corregido:
   está fuera de T30-04 y cambiarlo rompería `AuthSecurityIntegrationTest`, que
   es de otro. Documentado como riesgo residual; propongo tratarlo con el resto
   del flujo de estados de usuario (¿B1, con sesiones?).

7. **`X-Forwarded-For` se confía tal cual** (comportamiento preexistente del
   filtro). Solo es seguro si el backend está siempre detrás del proxy. Si A4
   toca despliegue, conviene confirmarlo.
