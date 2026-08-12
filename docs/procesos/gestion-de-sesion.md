# Gestión de la sesión

Una vez iniciada la sesión ([Inicio de sesión](inicio-de-sesion.md)), el usuario
tiene un access token JWT de vida corta y un refresh token opaco en cookie. Este
documento cubre qué pasa después: cómo se renueva la sesión, cómo se cierra, cómo
el usuario ve y revoca sus sesiones abiertas, y cómo el sistema corta el acceso
de un principal que ha dejado de ser válido sin esperar a que caduque su JWT.

La pieza más delicada es la **rotación del refresh token**: cada renovación
invalida el token usado y emite uno nuevo. Que reaparezca un token ya revocado
solo tiene una explicación razonable —alguien copió la cookie— y el sistema
reacciona a ello revocando la sesión entera.

## Actores

| Actor | Responsabilidad |
| --- | --- |
| Navegador | Guarda la cookie de refresh y la reenvía automáticamente. |
| Frontend | Reintenta con `refresh` cuando una petición devuelve `401`. |
| Backend | Rota tokens, detecta reusos y revoca sesiones. |

## Renovación de la sesión

```mermaid
sequenceDiagram
    autonumber
    participant F as Frontend
    participant B as Backend
    participant BD as Base de datos

    F->>B: Petición con el access token caducado
    B-->>F: 401
    F->>B: POST /api/v1/auth/refresh<br/>(cookie refresh_token)
    B->>BD: Busca por hash del token,<br/>con bloqueo pesimista

    alt Cookie ausente o token desconocido
        B-->>F: 401 token de refresco inválido
    else El token ya estaba revocado
        Note over B: Reuso: alguien está usando<br/>un token que ya se rotó
        B->>BD: Revoca la sesión completa<br/>y todos sus refresh tokens
        B-->>F: 401 reuso detectado
    else Token caducado
        B->>BD: Lo revoca
        B-->>F: 401 token de refresco inválido
    else Usuario o tenant ya no activos
        B-->>F: 401 USER_INACTIVE / TENANT_INACTIVE
    else Sesión no activa
        B->>BD: Revoca la sesión
        B-->>F: 401 token de refresco inválido
    else Camino normal
        B->>BD: Rota: el token actual apunta al nuevo
        B->>BD: Refresca la caducidad de la sesión
        B-->>F: 200 nuevo access token<br/>Set-Cookie: nuevo refresh_token
        F->>B: Reintenta la petición original
    end
```

La transacción se marca explícitamente para **no hacer rollback** ante el error
de reuso ni ante el de token inválido: si lo hiciera, las revocaciones que
acaban de aplicarse se perderían justo cuando más falta hacen.

## Ciclo de vida del refresh token

```mermaid
stateDiagram-v2
    [*] --> Activo: login o rotación
    Activo --> Rotado: refresh correcto
    Activo --> Revocado: logout, revocación de sesión,<br/>reset de contraseña o desactivación
    Activo --> Revocado: caduca y se usa
    Rotado --> ReusoDetectado: se vuelve a usar el token rotado
    ReusoDetectado --> [*]: se revoca la sesión entera
    Revocado --> [*]

    note right of Rotado
        Sigue en base de datos
        marcado como revocado:
        es lo que permite
        detectar el reuso.
    end note
```

## Cierre de sesión y revocación

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant B as Backend
    participant BD as Base de datos

    rect rgb(240, 244, 248)
    Note over U,BD: Logout
    U->>B: POST /api/v1/auth/logout
    alt Sin cookie
        B-->>U: 204 (no hace nada, es idempotente)
    else Con cookie
        B->>BD: Revoca la sesión<br/>y todos sus refresh tokens
        B-->>U: 204 + cookie borrada
    end
    end

    rect rgb(244, 240, 248)
    Note over U,BD: Gestión de sesiones abiertas
    U->>B: GET /api/v1/auth/sessions
    B-->>U: Sesiones activas, marcando cuál es la actual
    U->>B: DELETE /api/v1/auth/sessions/{id}
    B->>BD: Revoca esa sesión y sus tokens
    alt Era la sesión actual
        B-->>U: 204 + cookie borrada
    else Era otra sesión
        B-->>U: 204
    end
    U->>B: DELETE /api/v1/auth/sessions
    B->>BD: Revoca todas
    B-->>U: 204 + cookie borrada
    end
```

## Corte del acceso en caliente

Un JWT válido no basta. En cada petición autenticada, un filtro comprueba que el
usuario, su tenant y su sesión siguen activos; si alguno no lo está, corta con
`401` sin llegar al controlador, incluyendo `errorCode` y `correlationId` en el
cuerpo. Es lo que hace que suspender un tenant o desactivar a un empleado surta
efecto de inmediato, y no cuando caduque el access token.

```mermaid
flowchart LR
    A[Petición autenticada] --> B{¿Usuario activo?}
    B -->|No| E[401 con errorCode]
    B -->|Sí| C{¿Tenant activo?}
    C -->|No| E
    C -->|Sí| D{¿Sesión activa?}
    D -->|No| E
    D -->|Sí| OK[Continúa al controlador]
```

## Endpoints implicados

| Método | Ruta | Rol | Descripción |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/refresh` | público (usa la cookie) | Rota el refresh token y emite un nuevo access token. |
| `POST` | `/api/v1/auth/logout` | público (usa la cookie) | Revoca la sesión actual. Idempotente. |
| `GET` | `/api/v1/auth/sessions` | autenticado | Lista las sesiones activas del usuario. |
| `DELETE` | `/api/v1/auth/sessions/{id}` | autenticado | Revoca una sesión concreta. |
| `DELETE` | `/api/v1/auth/sessions` | autenticado | Revoca todas las sesiones del usuario. |

## Ramas de error y reglas

| Condición | Respuesta |
| --- | --- |
| Cookie ausente en `refresh` | `401` token de refresco inválido |
| Token desconocido | `401` token de refresco inválido |
| **Token ya revocado (reuso)** | `401` reuso detectado **y se revoca la sesión completa** |
| Token caducado | `401`, y el token queda revocado |
| Usuario desactivado o tenant no activo | `401 USER_INACTIVE` / `TENANT_INACTIVE` |
| Sesión no activa | `401`, y la sesión queda revocada |
| Logout sin cookie | `204`, sin efecto |

## Efectos

- Rotación del refresh token, con el anterior marcado como rotado —se conserva
  precisamente para poder detectar su reuso—, y `Set-Cookie` con el nuevo.
- Revocación en cascada de sesión y tokens en logout, revocación explícita,
  reuso detectado, [reset de contraseña](recuperacion-de-contrasena.md) y
  [desactivación del empleado](gestion-de-empleados.md).
- La cookie se limpia siempre que la operación deja al navegador sin sesión
  utilizable.

## Frontend

`frontend/src/app/core/services/auth.service.ts` guarda el access token en una
signal, nunca en almacenamiento persistente.
`core/interceptors/auth.interceptor.ts` añade la cabecera `Authorization` salvo
a las rutas públicas (login, refresh, recuperación de contraseña y
`/api/v1/public/`), y ante un `401` intenta una renovación y reintenta la
petición original —excepto sobre las propias rutas de refresh y de contraseña,
donde reintentar no tendría sentido.

Prueba de extremo a extremo: `frontend/e2e/sesiones.spec.ts`.

## Referencias

- ADR-0004 — JWT con refresh rotatorio en cookie `HttpOnly`
- `docs/security/auth-controls.md`, `docs/security/threat-model.md`
- Backend: `identity/application/RefreshSessionUseCase.java`,
  `identity/application/LogoutUserUseCase.java`,
  `identity/interfaces/rest/SessionController.java`,
  `identity/application/RevokeSessionUseCase.java`,
  `identity/application/RevokeAllSessionsUseCase.java`,
  `shared/infrastructure/security/UserStatusFilter.java`
