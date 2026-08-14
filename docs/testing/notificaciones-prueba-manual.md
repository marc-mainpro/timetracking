# Cómo forzar cada tipo de notificación a mano

Guía de prueba manual de los 16 tipos de `NotificationType` (épica T170,
ADR-0018). Todos los endpoints y cuerpos están verificados contra el código, no
deducidos de la documentación.

## Preparación

```bash
docker compose up -d --build
```

Necesitas `PLATFORM_ADMIN_EMAIL` y `PLATFORM_ADMIN_PASSWORD` en `.env` (si están
vacías no se crea ningún administrador de plataforma y la mitad de la guía no se
puede ejecutar).

| Servicio | URL |
|---|---|
| API | `http://localhost:8080` |
| Frontend | `http://localhost:4200` |
| Mailpit (buzón) | `http://localhost:8025` |

Tres actores, cada uno con su token:

```bash
API=http://localhost:8080
login() {  # login <email> <password>
  curl -s -X POST $API/api/v1/auth/login \
    -H 'Content-Type: application/json' \
    -H "X-Forwarded-For: 198.51.100.$((RANDOM % 254 + 1))" \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["accessToken"])'
}
```

> **La cabecera `X-Forwarded-For` no es opcional.** El login está limitado a 10
> intentos por minuto y por IP (RS-007); repartir las llamadas entre direcciones
> del rango de documentación evita chocar con el límite sin relajarlo.

Crea un tenant de trabajo con su administrador y un empleado:

```bash
PT=$(login "$PLATFORM_ADMIN_EMAIL" "$PLATFORM_ADMIN_PASSWORD")
S=$(date +%s)

curl -s -X POST $API/api/v1/platform/tenants -H "Authorization: Bearer $PT" \
  -H 'Content-Type: application/json' -d "{
    \"tenantName\":\"Tenant $S\",\"timezone\":\"Europe/Madrid\",
    \"adminEmail\":\"admin-$S@acme.test\",\"adminPassword\":\"supersecretpwd\",
    \"firstName\":\"Admin\",\"lastName\":\"Prueba\"}"

AT=$(login "admin-$S@acme.test" supersecretpwd)

curl -s -X POST $API/api/v1/employees -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' -d "{
    \"email\":\"empleado-$S@acme.test\",\"password\":\"employeepwd123\",
    \"firstName\":\"Empleado\",\"lastName\":\"Prueba\",\"roles\":[\"EMPLOYEE\"]}"

ET=$(login "empleado-$S@acme.test" employeepwd123)
```

### Nada es inmediato

Ningún aviso aparece en la misma respuesta HTTP que lo provoca. El recorrido es
**hecho de negocio → outbox → consumidor → notificación → correo**, con dos
tareas programadas en medio:

| Salto | Latencia típica |
|---|---|
| Hecho → notificación in-app | ~5 s (publicador del outbox, `PT5S`) |
| Notificación → correo en mailpit | +30 s (job de envío, `PT30S`) |

Comprobación de la bandeja de cualquier actor:

```bash
inbox() { curl -s "$API/api/v1/notifications?page=0&size=50" -H "Authorization: Bearer $1" | python3 -m json.tool; }
```

Y la vista cruda, útil para ver el canal y la ruta, que la API no expone enteros:

```bash
docker compose exec -T postgres psql -U timetracking -d timetracking \
  -c "select type, email_required, action_path, status from notification order by created_at desc limit 10;"
```

---

## Empleado

### `ACCOUNT_CREATED` — correo sí · `/auth/recuperar-contrasena`

Ya lo has disparado: **crear el empleado** en la preparación lo genera. También
lo genera crear un tenant (por su primer administrador) y aprobar un alta.

El cuerpo **no lleva credenciales** a propósito: dirige a la pantalla de
recuperación para que la persona establezca su propia contraseña.

### `SHIFT_ASSIGNED` — correo sí · `/shifts`

Necesita una plantilla de turno antes de poder asignar:

```bash
TPL=$(curl -s -X POST $API/api/v1/admin/shifts/templates -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"name":"General","startTime":"08:00","endTime":"16:00","plannedBreakMinutes":30}' \
  | python3 -c 'import json,sys;print(json.load(sys.stdin)["id"])')

EMP=$(curl -s "$API/api/v1/employees?page=0&size=50" -H "Authorization: Bearer $AT" \
  | python3 -c 'import json,sys;print([e["id"] for e in json.load(sys.stdin)["content"] if "empleado-" in e["email"]][0])')

curl -s -X POST $API/api/v1/admin/shifts/assignments -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d "{\"employeeId\":\"$EMP\",\"shiftTemplateId\":\"$TPL\",\"validFrom\":\"2026-09-01\",\"validTo\":\"2026-09-30\"}"
```

El aviso nombra el turno y su vigencia: «Se te ha asignado el turno «General» del
1 al 30 de septiembre de 2026». El nombre viaja en el evento como **foto del
momento**; las horas y la pausa no viajan, se leen por la API.

### `WORKDAY_ANOMALY_DETECTED` — correo sí · `/workdays`

Es el menos evidente: no hay ningún endpoint que "cree una anomalía". Se detectan
**al cerrar la jornada**, comparando con las reglas horarias del tenant. La vía
más corta es exigir pausa y no hacerla:

```bash
curl -s -X PUT $API/api/v1/admin/hourly-rules -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"maxDailyWorkMinutes":480,"requiredBreakMinutes":30,"roundingStepMinutes":null,"toleranceMinutes":0}'

curl -s -X POST $API/api/v1/workdays/start -H "Authorization: Bearer $ET" -H 'Content-Type: application/json' -d '{}'
curl -s -X POST $API/api/v1/workdays/current/end -H "Authorization: Bearer $ET" -H 'Content-Type: application/json' -d '{}'
```

Produce la anomalía `REQUIRED_BREAK_NOT_MET`, que el aviso traduce a «no se
alcanzó la pausa mínima obligatoria»: **el código nunca debe aparecer en el
texto**. La otra anomalía posible es `MAX_DAILY_WORK_EXCEEDED` («se superó el
máximo de horas diarias»), que exige superar `maxDailyWorkMinutes` y por tanto
una jornada larga de verdad; para provocarla a mano, baja el máximo a 1 minuto.

**Este mismo cierre dispara también `TEAM_WORKDAY_ANOMALY` al administrador**:
es el único hecho del sistema con dos plantillas. Ver abajo.

> **Verás más anomalías de las que provocaste, y no son duplicados.** Aprobar una
> corrección **reevalúa la jornada**, así que emite un segundo
> `WorkdayAnomalyDetected` si la jornada corregida sigue incumpliendo las reglas.
> Son dos hechos distintos, con `eventId` distinto, y la deduplicación no aplica.
> Si quieres contar avisos limpiamente, prueba las anomalías antes que las
> correcciones.

### `CORRECTION_APPROVED` · `CORRECTION_REJECTED` — correo sí · `/corrections`

Necesitan una jornada cerrada. Con la del paso anterior:

```bash
WD=$(curl -s "$API/api/v1/workdays?page=0&size=10" -H "Authorization: Bearer $ET" \
  | python3 -c 'import json,sys;print(json.load(sys.stdin)["content"][0]["id"])')

COR=$(curl -s -X POST $API/api/v1/workdays/$WD/corrections -H "Authorization: Bearer $ET" \
  -H 'Content-Type: application/json' -d '{
    "reason":"Olvidé fichar la salida",
    "proposedChanges":{"startedAt":"2026-08-13T07:00:00Z","endedAt":"2026-08-13T15:00:00Z","breaks":[]}}' \
  | python3 -c 'import json,sys;print(json.load(sys.stdin)["id"])')

# Aprobar (comentario opcional):
curl -s -X POST $API/api/v1/corrections/$COR/approve -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' -d '{"resolutionComment":"Conforme"}'

# O rechazar (comentario OBLIGATORIO, si falta responde 400):
curl -s -X POST $API/api/v1/corrections/$COR/reject -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' -d '{"resolutionComment":"No hay evidencia"}'
```

Una corrección ya resuelta no se puede resolver otra vez, así que para probar
ambos tipos necesitas dos correcciones.

### `ABSENCE_APPROVED` · `ABSENCE_REJECTED` — correo sí · `/absences`

Los tipos de ausencia se siembran **por el outbox** al crear el tenant: recién
creado, el catálogo está vacío unos segundos. Espera a que responda algo:

```bash
until [ "$(curl -s $API/api/v1/app/absence-types -H "Authorization: Bearer $ET" | python3 -c 'import json,sys;print(len(json.load(sys.stdin)))')" != "0" ]; do sleep 2; done

TIPO=$(curl -s $API/api/v1/app/absence-types -H "Authorization: Bearer $ET" | python3 -c 'import json,sys;print(json.load(sys.stdin)[0]["id"])')

ABS=$(curl -s -X POST $API/api/v1/app/absences -H "Authorization: Bearer $ET" \
  -H 'Content-Type: application/json' \
  -d "{\"absenceTypeId\":\"$TIPO\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-03\",\"reason\":\"Asuntos propios\"}" \
  | python3 -c 'import json,sys;print(json.load(sys.stdin)["id"])')

curl -s -X POST $API/api/v1/admin/absences/$ABS/approve -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' -d '{"resolutionComment":"Aprobado"}'
# o .../reject con el mismo cuerpo
```

> El alta de ausencia responde **200**, no 201, a diferencia del resto de
> creaciones de la API. Es una inconsistencia conocida del contrato.

### `ACCOUNT_DEACTIVATED` — correo sí · sin ruta

```bash
curl -s -X PATCH $API/api/v1/employees/$EMP/deactivate -H "Authorization: Bearer $AT"
```

**El correo es el único canal útil aquí**: la persona ya no puede iniciar sesión
para ver el aviso in-app. La fila in-app se crea igualmente, sin coste, y queda
como histórico si se reactiva la cuenta (`PATCH .../activate`).

Hazlo al final: desactivar al empleado te deja sin `ET` para el resto.

---

## Administrador de tenant

Los cuatro primeros avisos se dirigen al **rol**, no a una persona: los reciben
todos los `TENANT_ADMIN` **activos** del tenant. Para ver el fan-out de verdad,
crea un segundo administrador antes de disparar cualquiera de ellos:

```bash
curl -s -X POST $API/api/v1/employees -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' -d "{
    \"email\":\"admin2-$S@acme.test\",\"password\":\"supersecretpwd\",
    \"firstName\":\"Admin\",\"lastName\":\"Segundo\",\"roles\":[\"TENANT_ADMIN\"]}"
```

### `CORRECTION_REQUESTED` — correo sí · `/admin/corrections`

Lo dispara el `POST /api/v1/workdays/{id}/corrections` de arriba, el mismo que
inicia el flujo de corrección.

### `ABSENCE_REQUESTED` — correo sí · `/admin/absences`

Lo dispara el `POST /api/v1/app/absences` de arriba.

> Comprueba también lo que **no** debe pasar: al solicitante no se le avisa por
> esta vía, aunque además sea administrador. Si un `TENANT_ADMIN` pide su propia
> ausencia, no recibe `ABSENCE_REQUESTED`; los demás administradores sí.

### `TEAM_WORKDAY_ANOMALY` — **correo NO** · `/admin/reports`

Lo dispara el cierre de jornada con anomalía, el mismo hecho que avisa al
empleado. **Es el único tipo sin correo**, y el caso más interesante de probar:

```bash
docker compose exec -T postgres psql -U timetracking -d timetracking \
  -c "select type, email_required, status from notification where type like '%ANOMALY%';"
```

Lo correcto es ver `TEAM_WORKDAY_ANOMALY | f | PENDING` **para siempre**, y ningún
correo en mailpit para el administrador. No es un atasco: sin envío, nada la
mueve de estado. Por eso el panel de estado cuenta *trabajo encolado* y no *filas
en PENDING* — si contara filas, cada anomalía inflaría el pendiente sin que
hubiera nada roto.

### `TENANT_SUSPENDED` · `TENANT_REACTIVATED` · `TENANT_ARCHIVED` — correo sí · sin ruta

Los dispara plataforma sobre el tenant:

```bash
TID=$(curl -s "$API/api/v1/platform/tenants?page=0&size=50" -H "Authorization: Bearer $PT" \
  | python3 -c "import json,sys;print([t['id'] for t in json.load(sys.stdin)['content'] if 'Tenant $S' == t['name']][0])")

curl -s -X POST $API/api/v1/platform/tenants/$TID/suspend -H "Authorization: Bearer $PT" \
  -H 'Content-Type: application/json' -d '{"reason":"Impago"}'
curl -s -X POST $API/api/v1/platform/tenants/$TID/reactivate -H "Authorization: Bearer $PT"
curl -s -X POST $API/api/v1/platform/tenants/$TID/archive -H "Authorization: Bearer $PT" \
  -H 'Content-Type: application/json' -d '{"reason":"Cierre de la empresa"}'
```

`suspend` exige motivo; `archive` lo acepta opcional; `reactivate` no lleva
cuerpo. El motivo aparece en el cuerpo del aviso.

> **Ojo con el orden y con el canal.** Un tenant suspendido o archivado deja de
> operar (RF-TEN-009): sus administradores **no pueden iniciar sesión**, así que
> no verán el aviso in-app —solo el correo— hasta que se reactive. Archivar es
> terminal: ese aviso solo existirá como correo y como fila. Deja los tres para
> el final, o repite la reactivación antes de seguir probando.

---

## Administrador de plataforma

Estos avisos van al tenant de sistema, así que se leen con `PT`.

### `REGISTRATION_PENDING_REVIEW` — correo sí · `/platform/registrations`

Se dispara al **verificar el correo**, no al recibir la solicitud: no se anuncian
altas que quizá nunca lleguen a confirmarse. Hacen falta los dos pasos:

```bash
R=$(date +%s)
curl -s -X POST $API/api/v1/public/tenant-registrations \
  -H 'Content-Type: application/json' -H "X-Forwarded-For: 203.0.113.$((RANDOM % 254 + 1))" \
  -d "{\"companyName\":\"Empresa $R\",\"timezone\":\"Europe/Madrid\",
       \"firstName\":\"Olivia\",\"lastName\":\"Registro\",\"email\":\"owner-$R@acme.test\",
       \"password\":\"supersecretpwd\",\"acceptTerms\":true}"
```

Responde **202** sin revelar nada (RF-REG-005). Saca el token del correo de
verificación en mailpit y verifica:

```bash
TOKEN=$(curl -s "http://localhost:8025/api/v1/search?query=to%3Aowner-$R%40acme.test" \
  | python3 -c "
import json,sys,urllib.request,re
m=json.load(sys.stdin)['messages'][0]
d=json.load(urllib.request.urlopen('http://localhost:8025/api/v1/message/'+m['ID']))
print(re.search(r'token=([A-Za-z0-9._~-]+)', d['Text']).group(1))")

curl -s -X POST $API/api/v1/public/tenant-registrations/verify-email \
  -H 'Content-Type: application/json' -d "{\"token\":\"$TOKEN\"}"
```

El cuerpo del aviso nombra la organización, gracias al `companyName` que T170-07
añadió al payload del evento. Antes de verificar no debe existir ningún aviso de
esa empresa: merece la pena comprobar ese negativo.

### `SYSTEM_QUEUE_STUCK` — correo sí · `/platform/system-status`

**El más incómodo de forzar**, porque es el único que no nace de un hecho de
negocio sino de una condición: que alguna cola tenga mensajes que agotaron sus
reintentos. Además la vigilancia corre cada 15 minutos.

La vía honesta —dejar el SMTP inalcanzable hasta que una notificación agote sus
5 intentos— tarda unos 2,5 minutos de reintentos más hasta 15 de espera del job.
Para una prueba manual, es más práctico fabricar la condición y acortar el ciclo:

```bash
# 1) Una cola con trabajo fallido.
docker compose exec -T postgres psql -U timetracking -d timetracking \
  -c "update notification set status='FAILED' where status='SENT' and id in (select id from notification limit 1);"

# 2) Arranca el backend vigilando cada minuto en vez de cada 15.
docker compose stop backend
docker compose run -d --service-ports -e NOTIFICATION_QUEUEWATCH_POLLINTERVAL=PT1M backend
```

Comprueba primero que la condición se ve en el panel, que es de donde bebe el
job:

```bash
curl -s $API/api/v1/platform/system-status -H "Authorization: Bearer $PT" | python3 -m json.tool
# needsAttention debe ser true
```

Dos comportamientos que conviene verificar y que son la razón de ser de la tarea:

1. **Avisa una sola vez** mientras la condición persista. El disparo es el flanco
   —de «todo bien» a «hay fallos»—, no el estado; si avisara por estado, saldría
   un aviso en cada pasada.
2. **Vuelve a avisar** si la condición se resuelve y reaparece (`update ... set
   status='SENT'`, esperar una pasada, y volver a romper).

> El flanco se recuerda **en memoria**. Reiniciar el backend rearma el aviso: una
> condición que siguiera activa se notificaría otra vez. Es deliberado —es
> preferible a perder el aviso— pero significa que no puedes reiniciar el
> contenedor entre los pasos 1 y 2 sin falsear la prueba.

No olvides restaurar el backend normal al terminar:

```bash
docker compose up -d backend
```

---

## Comprobaciones transversales

Con todo disparado, estas cuatro consultas resumen si la épica se comporta:

```bash
# 1) Un tipo por fila, con su canal y su ruta.
docker compose exec -T postgres psql -U timetracking -d timetracking \
  -c "select type, email_required, action_path, status, count(*) from notification group by 1,2,3,4 order by 1;"
```

- Solo `TEAM_WORKDAY_ANOMALY` debe tener `email_required = f`.
- Solo esa debe quedarse en `PENDING` indefinidamente.
- `ACCOUNT_DEACTIVATED` y los tres del ciclo de vida del tenant no llevan ruta.

```bash
# 2) La cola no acumula trabajo real.
curl -s $API/api/v1/platform/system-status -H "Authorization: Bearer $PT" | python3 -m json.tool
```

`notifications.pending` debe volver a 0 aunque haya filas `PENDING` en la tabla:
mide trabajo encolado, no filas.

```bash
# 3) Los correos llevan enlace absoluto.
curl -s "http://localhost:8025/api/v1/search?query=subject%3A%22Ausencia%20pendiente%22"
```

El cuerpo debe contener `Abrirlo en la aplicación:` seguido de
`http://localhost:4200/admin/absences` (la base la pone
`NOTIFICATION_APP_BASE_URL`, no la fila). Una notificación sin `actionPath`
produce un correo correcto **sin** esa línea.

El saludo es «Hola:» a secas, sin nombre: personalizarlo obligaría a guardar el
nombre del destinatario junto a la notificación, y se descartó.

```bash
# 4) Ningún texto filtra un identificador técnico.
docker compose exec -T postgres psql -U timetracking -d timetracking -c \
  "select
     count(*) filter (where body ~ '\\[|\\]')                               as con_corchetes,
     count(*) filter (where body ~ '[0-9]{4}-[0-9]{2}-[0-9]{2}')          as con_fecha_iso,
     count(*) filter (where body ~ '[A-Z]{2,}_[A-Z_]+')                   as con_enum,
     count(*) filter (where body ~ '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}') as con_uuid
   from notification;"
```

Las cuatro columnas deben dar **0**. Es la comprobación que destapó que las
anomalías salían como `[REQUIRED_BREAK_NOT_MET]`.

Y revisa el correo de `ACCOUNT_CREATED`: debe llevar a la pantalla de
recuperación, nunca una credencial.

Y en el frontend (`http://localhost:4200/notifications`): cada tipo debe
mostrarse con su etiqueta en castellano —nunca el identificador crudo— y el
título debe ser pulsable **solo** cuando la notificación tiene `actionPath`.
