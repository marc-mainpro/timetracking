# ADR-0004: JWT + refresh token rotatorio en cookie HttpOnly

* Estado: accepted
* Fecha: 2026-07-17

## Contexto y problema

Se necesita un mecanismo de autenticación stateless para la API, seguro
frente a robo de tokens desde el frontend (SPA Angular), y compatible con
Spring Security.

## Decisión

* **Access token**: JWT firmado con **HS256**, secreto por variable de
  entorno, vía Spring Security `oauth2-resource-server`. Vida: 15 minutos.
* **Refresh token**: opaco, hasheado con **SHA-256** en la tabla
  `refresh_token`, **rotatorio** con detección de reutilización (si se
  reutiliza un refresh token ya rotado, se revoca la cadena). Se transporta
  en cookie `HttpOnly; Secure; SameSite=Strict`, con `path=/api/v1/auth`.
* **Passwords**: BCrypt (`DelegatingPasswordEncoder` por defecto de Spring).
* **Rate limiting de login**: Bucket4j en memoria, 10 req/min por IP.
* **Frontend**: el access token se mantiene en memoria (servicio Angular);
  el refresh token NUNCA se guarda en `localStorage`.

## Consecuencias

* (+) El access token de vida corta limita la ventana de uso si se filtra.
* (+) La rotación con detección de reutilización permite detectar robo de
  refresh token y revocar la sesión.
* (+) `HttpOnly`/`Secure`/`SameSite=Strict` reduce el riesgo de robo por XSS
  y CSRF.
* (-) Mayor complejidad de implementación que un JWT de larga duración sin
  rotación; se acepta por el beneficio de seguridad.
