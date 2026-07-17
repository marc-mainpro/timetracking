# T205 — Rate limiting y batería de pruebas de seguridad de autenticación

- Iteración: 2 · Depende de: T204 · Contexto: CONTEXT-GLOBAL §6-7, CONTEXT-API §3

## Objetivo
Proteger los endpoints de autenticación y consolidar la suite de seguridad.

## Detalle
1. Rate limiting con Bucket4j en memoria: filtro sobre `/api/v1/auth/login` y `/auth/register` (10 req/min por IP). Exceso → 429 Problem Details con `errorCode: RATE_LIMIT_EXCEEDED`.
2. Revisar cabeceras de seguridad en respuestas (X-Content-Type-Options, Cache-Control en auth, etc.) y añadir las que falten.
3. Suite `AuthSecurityIT` consolidando (si no existen ya de T204): anónimo→401 en recurso privado; token inválido→401; usuario inactivo→401; refresh reutilizado→cadena invalidada; 429 al exceder el límite; después de la ventana vuelve a permitir.
4. Documentar en `docs/security/` las decisiones (rate limit, cookies, CSRF) y actualizar el modelo de amenazas.

## Fuera de alcance
Rate limiting distribuido (Redis) — YAGNI en MVP.

## Criterios de aceptación
- `mvn verify` verde; 429 verificado por test de integración.

## Ficheros previstos
`shared/infrastructure/security/RateLimitFilter.java`, `AuthSecurityIT.java`, `docs/security/*.md`.
