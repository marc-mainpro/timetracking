# Controles de autenticación

## Rate limiting

- Se aplica **Bucket4j en memoria** sobre:
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/register`
- Límite por IP: **10 solicitudes por minuto** en configuración normal.
- El exceso responde `429` con Problem Details y `errorCode = RATE_LIMIT_EXCEEDED`.
- La clave de limitación separa IP y ruta, de modo que un exceso en `login`
  no bloquea automáticamente `register` ni viceversa.
- No se usa almacenamiento distribuido en el MVP; un despliegue multiinstancia
  requerirá una estrategia compartida en una iteración futura.

## Cookies y tokens

- El access token JWT HS256 se devuelve solo en el body.
- El refresh token opaco se envía solo en cookie:
  `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`.
- El refresh token se persiste hasheado con SHA-256.
- La rotación es obligatoria y la reutilización invalida la cadena activa del
  usuario.

## CSRF

- La API funciona en modo stateless con Bearer JWT para recursos protegidos.
- `CSRF` se mantiene deshabilitado en Spring Security porque no existe sesión
  de servidor ni autenticación por cookie para las operaciones de negocio.
- El refresh token sigue viajando en cookie, pero el riesgo se reduce con:
  - `SameSite=Strict`
  - `HttpOnly`
  - `Secure`
  - refresh limitado a `/api/v1/auth`

## Cabeceras HTTP

- Se mantienen las cabeceras de seguridad por defecto de Spring Security.
- Los endpoints de autenticación devuelven además respuestas marcadas como
  no cacheables (`Cache-Control: no-store`, `Pragma: no-cache`).
