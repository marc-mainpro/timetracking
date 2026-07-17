## T205 — Rate limiting y batería de pruebas de seguridad de autenticación

### Cambios

- Se añadió `RateLimitFilter` en `shared.infrastructure.security` con Bucket4j en memoria para:
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/register`
- El límite se configura por propiedades (`auth.rate-limit.capacity`, `auth.rate-limit.window`) y responde `429` Problem Details con `errorCode = RATE_LIMIT_EXCEEDED`.
- `SecurityConfig` registra el filtro antes de la autenticación anónima para que actúe también sobre endpoints públicos.
- Se marcaron las respuestas de autenticación/registro como no cacheables (`Cache-Control: no-store`, `Pragma: no-cache`).
- Se añadió la suite `AuthSecurityIntegrationTest` con ventana corta solo en test para validar seguridad y rate limiting de forma determinista.

### Pruebas (comandos ejecutados y resultado)

```text
cd backend && mvn -B verify
```

Resultado: **BUILD SUCCESS**. `Tests run: 91, Failures: 0, Errors: 0, Skipped: 0`.

Casos cubiertos específicamente en T205:

- anónimo -> 401 en recurso privado
- bearer inválido/expirado -> 401
- usuario inactivo -> 401 `USER_INACTIVE`
- refresh reutilizado -> cadena invalidada
- `429 RATE_LIMIT_EXCEEDED` en `login`
- `429 RATE_LIMIT_EXCEEDED` en `register`
- reapertura de la ventana tras el rate limit
- cabeceras de seguridad y no-cache en respuestas de auth

### Cobertura

- JaCoCo se mantiene en verde para los gates de `domain` y `application`.
- La nueva lógica HTTP de rate limiting y la consolidación de seguridad quedan cubiertas por integración real sobre MockMvc + PostgreSQL.

### Seguridad

- Rate limiting por IP con Bucket4j en memoria, sin añadir complejidad distribuida fuera del MVP.
- Los endpoints públicos de autenticación ya no pueden ser abusados sin respuesta explícita `429`.
- Las respuestas de auth añaden directivas de no cache para reducir persistencia accidental de tokens/respuestas sensibles.
- Se mantiene CSRF deshabilitado para API stateless y se documentan las compensaciones existentes sobre refresh cookie.

### Documentación actualizada

- `docs/security/auth-controls.md`
- `docs/security/threat-model.md`
- `docs/api/README.md`

### ADR

- No fue necesario crear un ADR nuevo: la decisión de usar Bucket4j en memoria ya estaba fijada en el contexto global/ADR-0004.

### Riesgos detectados

1. El rate limit es local al proceso; en despliegues multiinstancia no compartirá contador entre nodos. Se acepta por MVP y queda documentado.
2. El filtro usa IP de cliente (`X-Forwarded-For` si existe). En producción habrá que confiar solo en proxies conocidos cuando se introduzca infraestructura inversa real.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T205.
- La siguiente iteración correcta es `T301` para introducir `TenantContext` y preparar el tenant-aware access path.
