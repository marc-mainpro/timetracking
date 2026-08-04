# ADR-0013: Bloqueo de cuenta en agregado propio y rate limiting configurado por patrón

* Estado: accepted
* Fecha: 2026-08-04
* Tareas: T30-03 (RS-007), T30-04 (RF-USR-008, RS-008)

## Contexto y problema

La V2 exige dos defensas contra la fuerza bruta sobre autenticación:

* **RS-007**: rate limiting en los endpoints sensibles. Hoy existe
  `RateLimitFilter` (bucket4j en memoria) con una lista literal de dos rutas
  —`login` y `register`— y un único límite global. Faltan `refresh`,
  recuperación de contraseña y reenvío de verificación; los dos últimos son
  endpoints que **todavía no existen**, llegan en olas posteriores.
* **RS-008 / RF-USR-008**: bloqueo temporal de la cuenta tras N intentos
  fallidos, con umbral y duración configurables, reinicio tras autenticación
  correcta y auditoría.

Tres decisiones no son obvias y se toman aquí.

### 1. ¿Dónde vive el contador de intentos fallidos?

El diseño §8.5 pide registrar intentos fallidos, fecha del último intento y
fecha de desbloqueo. El sitio evidente es el agregado `User`: es un dato del
usuario y ya existe la tabla `app_user`.

### 2. ¿Qué responde el login cuando la cuenta está bloqueada?

Un error `ACCOUNT_LOCKED` es exactamente lo que necesita el usuario legítimo
para entender qué le pasa, y exactamente lo que necesita un atacante para
**enumerar cuentas**: basta con lanzar N intentos contra un email y ver si la
respuesta cambia para saber que ese email existe. El requisito pide las dos
cosas a la vez.

### 3. ¿Cómo se cubren endpoints que aún no existen?

Ampliar la lista literal del filtro con las rutas actuales deja fuera las
futuras, y confiar en que el agente que las publique se acuerde de añadirse a
una lista que vive en otro módulo es el patrón que ADR-0011 acaba de eliminar.

## Decisión

### 1. Agregado propio `AccountLockout`, tabla `account_lockout` (V14)

El estado de bloqueo **no** vive en `User`, sino en un agregado raíz propio con
identidad `userId` y tabla propia. Razones, por orden de peso:

* **Contención de escritura en el camino de login.** El contador se escribe en
  cada intento fallido, una frecuencia que decide el atacante, no el negocio.
  Dentro de `User` cada intento reescribiría la fila del usuario junto con su
  colección de roles.
* **Bloqueo optimista.** `User` es un agregado de negocio; si se le añade un
  contador de alta frecuencia, dos intentos simultáneos —o un intento
  concurrente con una edición legítima del perfil— compiten por la misma
  versión y producen `CONCURRENT_MODIFICATION` en operaciones que no tienen
  nada que ver con el bloqueo. Un detalle de seguridad no debe degradar la
  concurrencia del agregado central de identidad.
* **Ciclo de vida distinto.** El bloqueo es dato operativo, purgable y sin
  valor histórico; el usuario no.
* **Invariante local.** Todas las reglas del bloqueo (umbral, ventana,
  expiración, reinicio) se resuelven mirando solo el propio bloqueo. No hay
  invariante que abarque usuario y bloqueo a la vez, que es el criterio para
  separar agregados.

`account_lockout` guarda `tenant_id` redundante con `app_user` para que las
consultas sean tenant-scoped sin JOIN, y la entidad JPA **no lleva `@Version`**:
perder un incremento por escritura concurrente concede como mucho un intento
extra, mientras que fallar el login por un conflicto optimista sería un error
visible para el usuario.

### 2. Registro de fallos en transacción independiente

Un login fallido termina lanzando `InvalidCredentialsException`, que hace
rollback de la transacción del caso de uso. Si el incremento del contador
viajase en ella se perdería y el bloqueo **nunca llegaría a dispararse**. Por
eso `AccountLockoutService.registerFailedAttempt` es `REQUIRES_NEW`. El reinicio
tras login correcto sí participa en la transacción del caso de uso: si la
emisión de la sesión falla, el reinicio tampoco debe darse por bueno.

### 3. Anti-enumeración: el bloqueo se revela solo a quien acierta la contraseña

La contraseña se comprueba **siempre**, también contra una cuenta bloqueada, y
solo después se decide la respuesta:

| Contraseña | Cuenta | Respuesta |
|---|---|---|
| incorrecta | inexistente | 401 `INVALID_CREDENTIALS` |
| incorrecta | existente, no bloqueada | 401 `INVALID_CREDENTIALS` |
| incorrecta | existente, **bloqueada** | 401 `INVALID_CREDENTIALS` |
| correcta | existente, bloqueada | 401 `ACCOUNT_LOCKED` |

Quien no conoce la contraseña no puede distinguir ninguno de los tres primeros
casos: el bloqueo deja de ser un oráculo de enumeración. Quien sí la conoce es,
por definición, el titular de la cuenta, y recibe el mensaje que necesita. El
cuerpo completo del Problem Details se compara byte a byte en
`AccountLockoutIntegrationTest#lockedAccountIsIndistinguishableFromAnUnknownAccount`.

Un intento contra una cuenta ya bloqueada **no alarga el bloqueo**: si lo
hiciera, un atacante podría mantener bloqueada la cuenta de un tercero
indefinidamente, convirtiendo el control de seguridad en una denegación de
servicio dirigida. Sí se audita, porque es la señal de que el ataque continúa.

### 4. Rate limiting configurado por patrón de ruta

`RateLimitFilter` deja de tener rutas en el código: las reglas son
configuración (`auth.rate-limit.endpoints[]` en `config/account-lockout.yml`),
con patrones Ant y no rutas literales, y con `capacity`/`window` opcionales por
regla que heredan del límite global cuando faltan. Así:

* Los límites son ajustables sin recompilar, que es lo que pide T30-03.
* `/api/v1/auth/password/**` y `/api/v1/auth/verification/**` quedan cubiertos
  **antes de existir**: los endpoints de las olas 2+ nacen protegidos sin que su
  autor tenga que tocar un fichero de `shared`.
* `refresh` recibe un límite propio más alto (30/min frente a 10/min): es una
  operación legítima y frecuente y varios empleados tras el mismo NAT comparten
  IP; con el límite de login se expulsaría a usuarios de sesiones válidas.
* Recuperación de contraseña y reenvío de verificación reciben un límite bajo
  con ventana larga (5/15 min): cada petición envía un correo, así que el abuso
  no es solo fuerza bruta sino usar el sistema para bombardear un buzón ajeno.

Si no hay ninguna regla configurada, el filtro **no se desactiva**: cae al
mínimo histórico (login y registro). Perder un fichero de configuración no debe
apagar una defensa en silencio.

### 5. Métricas

`AuthenticationMetrics` expone `auth.login.failed{reason}`,
`auth.login.succeeded` y `auth.accounts.locked` vía Micrometer/Actuator,
siguiendo el patrón de `OutboxMetrics`. Deliberadamente **sin** etiqueta de
tenant, usuario ni IP: además de cardinalidad no acotada, una serie temporal
—que no tiene control de acceso por tenant— se convertiría en otra vía de
enumeración de usuarios. El detalle por usuario está en la auditoría, que sí es
tenant-scoped.

## Consecuencias

* (+) El camino de login escribe en una tabla pequeña y dedicada, sin tocar
  `app_user` ni su bloqueo optimista.
* (+) El bloqueo es persistente y común a todas las instancias: es la defensa
  que **no** depende de la memoria del proceso, y por tanto la que sigue en pie
  donde el rate limiting flaquea.
* (+) Los endpoints sensibles de olas futuras nacen protegidos sin coordinación
  entre agentes.
* (+) La respuesta del login no permite enumerar cuentas ni averiguar cuáles se
  han conseguido bloquear.
* (−) Una consulta más por login (la del bloqueo). Es una lectura por PK sobre
  una tabla estrecha; se acepta frente al coste de la alternativa.
* (−) Sin `@Version`, dos fallos simultáneos pueden perder un incremento. El
  efecto máximo es un intento extra antes de bloquear; irrelevante frente a un
  umbral de 5.
* (−) `AuditRecorder` gana una segunda operación (con tenant y actor
  explícitos) y deja de ser interfaz funcional: un test que la implementaba con
  lambda pasa a clase anónima. Era inevitable —un login fallido debe auditarse
  precisamente cuando **no** hay principal autenticado del que leer el tenant—
  y es preferible a que `JpaAuditRecorder` adivine el tenant.
* (−) Auditar cada intento fallido hace crecer `audit_event` a un ritmo que
  marca el atacante. Queda acotado por el rate limiting por IP y por el propio
  bloqueo, que corta el flujo de eventos `LOGIN_FAILED` en cuanto salta.

## Riesgo residual: bucket4j en memoria

Los contadores de rate limiting viven en la memoria del proceso. Con varias
instancias tras un balanceador el límite efectivo se multiplica por el número
de réplicas, y un reinicio lo reinicia. Ya está aceptado como riesgo residual en
`docs/security/owasp-review.md` y **no se resuelve aquí**: introducir Redis o
cualquier almacén compartido significaría una dependencia nueva, que la V2
prohíbe fuera de la Ola 0. La mitigación real dentro del alcance actual es que
el bloqueo por cuenta (RS-008) sí es persistente y compartido, así que la
defensa contra el ataque distribuido no depende del limitador en memoria. La
solución definitiva es un limitador en el borde (WAF o ingress), decisión de
despliegue y no de código.

## Alternativas descartadas

* **Columnas `failed_attempts` / `locked_until` en `app_user`.** Menos ficheros,
  pero mete un contador de alta frecuencia controlado por el atacante dentro del
  agregado de negocio más consultado del sistema, con la contención y los
  conflictos optimistas que eso implica.
* **Responder siempre `ACCOUNT_LOCKED` cuando la cuenta lo está.** Mensaje más
  claro, pero convierte el login en un oráculo de enumeración de usuarios y
  además revela al atacante qué cuentas ha conseguido bloquear.
* **Responder siempre `INVALID_CREDENTIALS`, también al titular.** Máxima
  opacidad, pero deja al usuario legítimo sin explicación de por qué su
  contraseña correcta no funciona; es exactamente el caso que RF-USR-008 quiere
  cubrir.
* **Bloqueo en memoria (caché) en vez de en base de datos.** Más rápido y sin
  migración, pero se pierde en cada reinicio y no se comparte entre instancias:
  heredaría justo la limitación que este ADR intenta no duplicar.
* **Contador por IP en lugar de por cuenta.** Ya lo cubre el rate limiting; no
  protege del ataque distribuido, que es exactamente lo que aporta el bloqueo
  por cuenta.
* **Mantener la lista de rutas en el código del filtro.** Obliga a cada módulo
  nuevo a editar `shared` para protegerse, justo lo que ADR-0011 elimina.
* **Redis para rate limiting distribuido.** Resuelve el riesgo residual pero
  añade una dependencia y un servicio nuevos, fuera del alcance permitido.
