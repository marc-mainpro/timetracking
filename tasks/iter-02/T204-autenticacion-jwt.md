# T204 — Autenticación: login, refresh rotatorio, logout

- Iteración: 2 · Depende de: T203 · Contexto: CONTEXT-GLOBAL §3 y §6, CONTEXT-API §2

## Objetivo
Autenticación completa con access token JWT corto y refresh token rotatorio en cookie HttpOnly.

## Detalle
1. `identity.application`: casos de uso `AuthenticateUser`, `RefreshSession`, `LogoutUser`.
   - Login: verifica password (BCrypt), usuario ACTIVO y tenant ACTIVO (si no → 401 con `USER_INACTIVE`/`TENANT_INACTIVE` sin filtrar cuál existe); emite access token JWT (HS256, secreto por env `JWT_SECRET`, TTL 15 min, claims: `sub`=userId, `tenantId`, `roles`) y refresh token opaco (aleatorio 256 bits, se guarda SHA-256 en `refresh_token`, TTL 14 días).
   - Refresh: valida hash, no expirado, no revocado; **rotación**: revoca el actual (`replaced_by`) y emite uno nuevo. **Detección de reutilización**: si llega un token ya revocado, revocar toda la cadena del usuario → 401.
   - Logout: revoca refresh y limpia cookie.
2. Cookie: `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`. Access token solo en el body de la respuesta.
3. `SecurityConfig`: resource server JWT; públicos solo `/auth/register|login|refresh`, actuator health; resto autenticado; method security por roles habilitada; CORS restringido a origen del frontend por env; cabeceras de seguridad por defecto de Spring Security; CSRF deshabilitado para API Bearer (documentar por qué: sin sesión de cookie para endpoints de negocio).
4. Convertir claims → `Authentication` con authorities `ROLE_TENANT_ADMIN`/`ROLE_EMPLOYEE`.

## Pruebas
- Unitarias: rotación, reutilización revoca cadena, expiración, usuario/tenant inactivo.
- Integración: login feliz devuelve access + cookie; refresh rota; refresh reutilizado → 401 y cadena invalidada; logout revoca; token inválido/expirado → 401; anónimo a recurso privado → 401. No aparecen tokens/contraseñas en logs.

## Fuera de alcance
Rate limiting (T205), TenantContext (T301).

## Criterios de aceptación
- Todas las pruebas de seguridad de la sección 11 del SDD relativas a auth pasan; `mvn verify` verde; OpenAPI actualizado.

## Ficheros previstos
`identity/application/**`, `identity/infrastructure/security/**`, `identity/interfaces/rest/AuthController.java`, `shared/infrastructure/configuration/SecurityConfig.java`, tests.
