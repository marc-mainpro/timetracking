# Alta de una empresa

Es el proceso por el que una organización que no existe en el sistema acaba
teniendo un tenant operativo y un primer administrador. No es una única
operación: son **tres decisiones separadas en el tiempo**, y por tres actores
distintos.

1. Alguien anónimo rellena el formulario público y crea una **solicitud de
   alta**. No se crea ningún tenant ni ningún usuario (ADR-0016).
2. Ese alguien demuestra que controla el correo indicado, pulsando el enlace de
   verificación. La solicitud queda pendiente de revisión.
3. Un `PLATFORM_ADMIN` aprueba o rechaza. Solo al aprobar nacen el tenant —en
   estado `PENDING`, todavía sin poder operar— y su primer `TENANT_ADMIN`.

La separación entre solicitud y tenant es deliberada: permite que cualquiera
pida el alta sin que un formulario público pueda crear entidades reales en el
sistema. La activación del tenant es todavía un cuarto paso, documentado en
[Ciclo de vida del tenant](ciclo-de-vida-del-tenant.md).

## Actores

| Actor | Responsabilidad |
| --- | --- |
| Visitante anónimo | Rellena el formulario y confirma su correo. No está autenticado. |
| Backend | Valida, aplica límites de abuso, guarda la solicitud y genera el token de verificación. |
| Correo | Entrega el enlace de verificación. El envío ocurre fuera de la transacción (ADR-0012). |
| `PLATFORM_ADMIN` | Revisa la solicitud verificada y decide aprobar o rechazar. |

## Diagrama de secuencia

```mermaid
sequenceDiagram
    autonumber
    actor V as Visitante
    participant F as Frontend (/registro)
    participant B as Backend
    participant BD as Base de datos
    participant O as Outbox + job
    participant M as Correo
    actor P as PLATFORM_ADMIN

    V->>F: Rellena empresa, zona horaria,<br/>datos del propietario y contraseña
    F->>B: POST /api/v1/public/tenant-registrations
    alt Registro público deshabilitado
        B-->>F: 403 Forbidden
    else Habilitado
        B->>B: Valida formato y calcula el hash<br/>de la contraseña y de la IP
        Note over B: A partir de aquí todos los caminos<br/>responden 202 con el mismo cuerpo
        B->>BD: Crea la solicitud en<br/>PENDING_EMAIL_VERIFICATION<br/>+ hash del token de verificación
        B->>BD: Escribe los eventos en el outbox<br/>(misma transacción)
        B-->>F: 202 Accepted (mensaje genérico)
        F-->>V: «Revisa tu correo»
    end

    Note over BD,O: Transacción confirmada.<br/>El job de outbox publica después.
    O->>M: Envía el enlace de verificación<br/>(token en claro, TTL configurable)
    M-->>V: Correo con /registro/verificar?token=...

    V->>F: Abre el enlace del correo
    F->>B: POST /api/v1/public/tenant-registrations/verify-email
    B->>BD: Busca la solicitud por hash del token
    alt Token desconocido o vacío
        B-->>F: Error de token inválido
    else Token caducado
        B->>BD: Marca la solicitud EXPIRED
        B-->>F: Error de token inválido (mismo mensaje)
    else Token válido
        B->>BD: Estado → PENDING_REVIEW
        B-->>F: 200 «Pendiente de revisión»
    end

    P->>B: GET /api/v1/platform/registrations?status=PENDING_REVIEW
    alt Aprueba
        P->>B: POST /api/v1/platform/registrations/{id}/approve
        alt Solicitud ya CONSUMED
            B-->>P: Devuelve el mismo resultado (idempotente)
        else El correo se registró entretanto
            B-->>P: 409 Correo ya en uso — se aborta la aprobación
        else Camino normal
            B->>BD: Crea el Tenant en PENDING
            B->>BD: Crea el usuario TENANT_ADMIN
            B->>BD: Solicitud → APPROVED → CONSUMED
            B-->>P: 200 con el tenant creado
            O->>B: tenant.registration-approved.v1
            B->>BD: Siembra los tipos de ausencia por defecto
        end
    else Rechaza
        P->>B: POST /api/v1/platform/registrations/{id}/reject<br/>(motivo obligatorio)
        B->>BD: Estado → REJECTED con el motivo
        B-->>P: 200
    end
```

## Caminos silenciosos del formulario público

El endpoint de solicitud responde **siempre** `202 Accepted` con el mismo
cuerpo. Un atacante que pruebe correos no puede distinguir cuál de estos
caminos tomó el sistema (RF-REG-005). El hash de la contraseña se calcula
*antes* de decidir, para que tampoco el tiempo de respuesta delate el camino.

```mermaid
flowchart TD
    A[POST /public/tenant-registrations] --> B{¿Formato de correo válido?}
    B -->|No| E400[400 Bad Request]
    B -->|Sí| H[Hash de contraseña e IP]
    H --> T{¿Supera el límite<br/>por IP o por correo<br/>en la ventana?}
    T -->|Sí| S[Descarta en silencio]
    T -->|No| U{¿El correo ya tiene<br/>cuenta de usuario?}
    U -->|Sí| S
    U -->|No| O{¿Hay ya una solicitud<br/>abierta con ese correo?}
    O -->|Sí| R{¿Admite reenvío?}
    R -->|Sí| RS[Reenvía la verificación]
    R -->|No| S
    O -->|No| N[Crea la solicitud<br/>y envía la verificación]
    S --> RESP[202 Accepted<br/>cuerpo genérico]
    RS --> RESP
    N --> RESP
```

El endpoint de reenvío explícito
(`POST /public/tenant-registrations/resend-verification`) se comporta igual:
correo malformado, correo desconocido, solicitud ya verificada y reenvíos
agotados producen los cuatro el mismo `202`.

## Estados de la solicitud

```mermaid
stateDiagram-v2
    [*] --> PENDING_EMAIL_VERIFICATION: se envía el formulario
    PENDING_EMAIL_VERIFICATION --> PENDING_EMAIL_VERIFICATION: reenvío de verificación<br/>(hasta el máximo configurado)
    PENDING_EMAIL_VERIFICATION --> PENDING_REVIEW: token verificado
    PENDING_EMAIL_VERIFICATION --> EXPIRED: se usa un token caducado
    PENDING_REVIEW --> APPROVED: aprueba PLATFORM_ADMIN
    PENDING_REVIEW --> REJECTED: rechaza PLATFORM_ADMIN<br/>(motivo obligatorio)
    APPROVED --> CONSUMED: tenant y propietario creados
    CONSUMED --> [*]
    REJECTED --> [*]
    EXPIRED --> [*]
```

`APPROVED` y `CONSUMED` ocurren dentro de la **misma transacción**: no existe
un instante observable con la solicitud aprobada y sin tenant. Se modelan como
estados distintos para que una segunda aprobación encuentre `CONSUMED` y
devuelva el tenant existente en lugar de crear otro.

Los estados `PENDING_EMAIL_VERIFICATION`, `PENDING_REVIEW` y `APPROVED` cuentan
como «solicitud abierta»: bloquean una nueva solicitud con el mismo correo.

## Endpoints implicados

| Método | Ruta | Rol | Descripción |
| --- | --- | --- | --- |
| `POST` | `/api/v1/public/tenant-registrations` | público | Crea la solicitud. Siempre `202`. |
| `POST` | `/api/v1/public/tenant-registrations/verify-email` | público | Consume el token de verificación. |
| `POST` | `/api/v1/public/tenant-registrations/resend-verification` | público | Reenvía el correo. Siempre `202`. |
| `GET` | `/api/v1/platform/registrations` | `PLATFORM_ADMIN` | Lista solicitudes, filtro opcional por estado. |
| `POST` | `/api/v1/platform/registrations/{id}/approve` | `PLATFORM_ADMIN` | Crea tenant `PENDING` + primer admin. |
| `POST` | `/api/v1/platform/registrations/{id}/reject` | `PLATFORM_ADMIN` | Rechaza con motivo obligatorio. |

## Ramas de error y reglas

| Condición | Resultado |
| --- | --- |
| `registration.public.enabled=false` | `403` en los tres endpoints públicos. |
| Correo con formato inválido en el alta | `400`, antes de cualquier consulta a base de datos. |
| Límite de solicitudes por IP o por correo superado | Descartado en silencio, `202`. |
| El correo ya tiene cuenta de usuario | Descartado en silencio, `202`. |
| Ya existe una solicitud abierta con ese correo | Se reenvía la verificación en lugar de duplicar. |
| Token de verificación vacío, desconocido o caducado | Mismo error genérico. Si estaba caducado, la solicitud pasa a `EXPIRED`. |
| Aprobar una solicitud ya `CONSUMED` | Idempotente: devuelve la misma solicitud, no crea un segundo tenant. |
| Aprobar cuando el correo se registró entretanto | `409`. Se aborta antes que dejar un tenant sin administrador. |
| Rechazar sin motivo | `400`: el motivo es obligatorio. |
| Aprobar o rechazar desde un estado incompatible | Conflicto de transición del agregado. |

Notas de seguridad: el token de verificación se guarda **hasheado**; el token en
claro solo viaja en el correo y nunca se registra en logs. La IP del solicitante
se almacena hasheada (SHA-256), únicamente para aplicar el límite de abuso.

## Efectos

**Eventos de integración** (escritos en el outbox dentro de la transacción de
negocio, publicados después por el job):

- `tenant.registration-requested.v1`
- `tenant.registration-verification-requested.v1` — lo consume el listener que
  envía el correo, deduplicando por `(eventId, consumidor)`.
- `tenant.registration-email-verified.v1`
- `tenant.registration-approved.v1` — además de informar, **dispara la siembra
  de los cinco tipos de ausencia por defecto** del tenant recién creado.
- `tenant.registration-rejected.v1`
- `tenant.registered.v1` e `identity.employee-created.v1`, al materializarse el
  tenant y su propietario.

**Auditoría**: `TENANT_REGISTRATION_REQUESTED`, `..._VERIFICATION_RESENT`,
`..._EMAIL_VERIFIED`, `..._EXPIRED`, `..._APPROVED`, `..._REJECTED`.

**Correo**: un mensaje de verificación por cada alta y por cada reenvío, con la
URL construida a partir de la plantilla configurada.

## Frontend

| Pantalla | Ruta | Papel |
| --- | --- | --- |
| Formulario de alta | `/registro` | Empresa, zona horaria (por defecto `Europe/Madrid`), datos del propietario, contraseña y aceptación de condiciones. |
| Verificación | `/registro/verificar?token=…` | Verifica al cargar; si falla ofrece un formulario de reenvío. |
| Revisión de solicitudes | `/platform/registrations` | Aprobar/rechazar. Tras aprobar avisa de que el tenant nace `PENDING`. |

Componentes: `frontend/src/app/features/registration/` y
`frontend/src/app/features/platform/platform-registrations.component.ts`.
Prueba de extremo a extremo: `frontend/e2e/registro-publico.spec.ts`.

## Referencias

- ADR-0016 — solicitud de alta separada del tenant
- ADR-0012 — envío de correo fuera de la transacción
- ADR-0005 — transactional outbox
- `docs/integration/event-catalog.md` — contrato de los eventos `tenant.registration-*`
- Backend: `tenant/interfaces/rest/PublicTenantRegistrationController.java`,
  `PlatformTenantRegistrationController.java`,
  `tenant/application/RequestTenantRegistrationUseCase.java`,
  `VerifyTenantRegistrationEmailUseCase.java`,
  `ResendTenantRegistrationVerificationUseCase.java`,
  `ApproveTenantRegistrationUseCase.java`,
  `RejectTenantRegistrationUseCase.java`,
  `tenant/domain/TenantRegistrationStatus.java`,
  `absence/application/SeedDefaultAbsenceTypesListener.java`
