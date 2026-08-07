# Revisión OWASP

## Alcance y fecha

Revisión de la V2 completada el 2026-08-07 (T160-02). Recorre A01–A10 sobre el
estado actual del código, no sobre el del MVP. Los hallazgos abiertos y los
riesgos aceptados, con su fecha de revisión, están al final de cada sección.

## Resumen

Checklist aplicado sobre el backend, frontend, `docker compose` y CI. La
revisión se valida con `mvn -B verify`, `npm run test:coverage`, pruebas de
seguridad específicas y análisis manual del código y configuración.

## A01 Broken Access Control

- Aplica: sí.
- Mitigación: Spring Security exige autenticación por defecto y `@PreAuthorize`
  por rol en los endpoints privilegiados. El tenant y el usuario salen siempre
  del principal, nunca de la petición. Un recurso ajeno responde 404 y no 403,
  para no confirmar su existencia.
- Evidencia: `RouteAuthorizationIntegrationTest`,
  `CrossTenantSecurityIntegrationTest`, `PrivilegedEndpointsRequireRoleTest`,
  `frontend/e2e/aislamiento.spec.ts`, suites REST de cada módulo.
- Revisado en T160-02: se auditaron los 22 controladores uno a uno. Ninguno
  carecía de control de rol por descuido; los cinco sin `@PreAuthorize` son
  públicos o de recurso propio, acotados por el usuario del principal.
- El riesgo real no era el estado actual sino el siguiente controlador: la
  cadena solo garantiza `anyRequest().authenticated()`, así que un endpoint
  nuevo bajo `/admin` o `/platform` sin `@PreAuthorize` quedaría abierto a
  cualquier usuario autenticado, respondería 200 y nada fallaría. Convertido en
  regla automática (`PrivilegedEndpointsRequireRoleTest`), con comprobación de
  que no pasa por vacío y verificada con una violación inyectada a propósito.

## A02 Cryptographic Failures

- Aplica: sí.
- Mitigación: contraseñas con BCrypt, JWT HS256 con secreto externo, refresh
  token opaco almacenado hasheado, cookies `HttpOnly` y `SameSite=Strict`.
- Evidencia: `AuthControllerIntegrationTest`, `AuthSecurityIntegrationTest`,
  `docs/security/threat-model.md`.

## A03 Injection

- Aplica: sí.
- Mitigación: JPA parametrizada, validación de DTOs, Problem Details genérico
  ante errores inesperados, sin exponer SQL.
- Evidencia: `GlobalExceptionHandlerIntegrationTest`, DTOs validados en
  correcciones y autenticación.

## A04 Insecure Design

- Aplica: sí.
- Mitigación: tenant resuelto siempre desde el principal autenticado, outbox
  transaccional, auditoría append-only, bloqueo temporal de cuenta, rate
  limiting por patrón de ruta, ciclo de vida del tenant que corta el acceso al
  suspenderlo, y alta pública en tres pasos con aprobación explícita en lugar de
  creación directa.
- Evidencia: ADR-0005, ADR-0008, ADR-0009, ADR-0014, ADR-0016, `EndToEndFlowIT`,
  `frontend/e2e/` (20 casos sobre la pila real).
- Revisado en T160-02: el alta pública ya no crea un tenant operativo en un
  paso; requiere verificar el correo, que plataforma apruebe y que active. Los
  tres pasos están cubiertos por `registro-publico.spec.ts`.

## A05 Security Misconfiguration

- Aplica: sí.
- Mitigación: CORS restringido a `FRONTEND_ORIGIN`, cabeceras `nosniff`,
  `SAMEORIGIN`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store` en
  respuestas sensibles, payload máximo 64 KiB en backend/nginx.
- Evidencia: `AuthSecurityIntegrationTest`, `frontend/nginx.conf`,
  `docker-compose.yml`.

## A06 Vulnerable and Outdated Components

- Aplica: sí.
- Mitigación: versiones explícitas soportadas (Spring Boot 3.5.9, Angular 19,
  PostgreSQL 16, Testcontainers 1.20.x). CI construye y prueba backend,
  frontend e imágenes Docker. Desde T30-05 la CI ejecuta además escaneo
  automatizado de dependencias: `npm audit` con gate propio en el frontend y
  OWASP dependency-check en el backend, ambos con umbral de bloqueo en
  severidad *high* y excepciones nominales con fecha de caducidad.
- Evidencia: `.github/workflows/ci.yml` (jobs `frontend-dependencies` y
  `backend-dependencies`), `scripts/security/npm-audit-gate.sh`,
  `backend/dependency-check-suppressions.xml`,
  `docs/security/dependency-scanning-policy.md`.
- El riesgo aceptado anterior («no se añade aún un escáner automatizado
  dedicado tipo Dependency-Check») queda **cerrado** por T30-05.
- Riesgo aceptado (nuevo, acotado): el análisis del **backend** requiere el
  secreto `NVD_API_KEY`, todavía no dado de alta en el repositorio de GitHub.
  Mientras no exista, el job avisa y no bloquea, de modo que RS-015 solo está
  cubierto de forma efectiva para el frontend. Revisión: 2026-11-04.
- Riesgo aceptado (nuevo, acotado): 24 advisories *high/critical* de `npm audit`
  con excepción aprobada y caducidad 2026-11-04; 5 son de runtime (cadena
  Angular 19, corregibles solo con Angular 21) y 19 de la cadena de build, que
  no se empaqueta en la imagen del frontend. Detalle y justificación por
  advisory en `scripts/security/npm-audit-allowlist.txt`.

## A02+A05 Escaneo de secretos en el repositorio

- Aplica: sí.
- Mitigación: la CI ejecuta gitleaks 8.30.1 sobre **toda la historia de git**
  (`fetch-depth: 0`), no solo sobre el diff: un secreto borrado en el commit
  siguiente sigue estando comprometido. Cualquier hallazgo rompe el build; no
  hay umbral de severidad.
- Evidencia: `.github/workflows/ci.yml` (job `secret-scan`), `.gitleaks.toml`.
- Estado: 0 hallazgos sobre 124 commits. Los 6 hallazgos previos eran
  placeholders (`replace-with-at-least-32-bytes-secret` en las plantillas
  `.env.example`, `test-jwt-secret-key-with-at-least-32-bytes` en el perfil de
  test). Se excluye el literal exacto, no el fichero, de modo que un secreto
  real commiteado en esos mismos ficheros sigue detectándose; verificado con
  prueba negativa.

## A07 Identification and Authentication Failures

- Aplica: sí.
- Mitigación: login uniforme, rate limiting, bloqueo temporal de cuenta,
  rotación de refresh token, revocación por reutilización, invalidación por
  usuario o tenant inactivo, y recuperación de contraseña con token de un solo
  uso que revoca las sesiones abiertas.
- Evidencia: `AuthSecurityIntegrationTest`, `AuthControllerIntegrationTest`,
  `AuthenticateUserUseCaseTest`, `AccountLockoutIntegrationTest`.

### Hallazgo cerrado en T160-02: el login era un oráculo de existencia

El estado de la cuenta y del tenant se comprobaba **antes** que la contraseña,
de modo que un correo desconocido respondía `INVALID_CREDENTIALS` y uno real
desactivado `USER_INACTIVE` o `TENANT_INACTIVE`. Bastaba leer el `errorCode`
para saber qué cuentas existen, sin conocer ninguna contraseña. La misma clase
declaraba en su documentación la propiedad contraria para el bloqueo de cuenta,
así que la garantía existía a medias.

Había además un canal lateral por **tiempo**: con un correo inexistente se
respondía sin ejecutar BCrypt, y la diferencia de latencia delataba la
inexistencia aunque el cuerpo fuese idéntico.

Corregido: el estado solo se revela a quien ya ha acertado la contraseña —es
decir, al dueño de la cuenta, que necesita saber por qué no puede entrar— y con
un correo inexistente se ejecuta igualmente una comparación contra un hash de
descarte para igualar el coste. Cubierto por
`AuthenticateUserUseCaseTest#doesNotRevealThatADeactivatedAccountExists`,
`#doesNotRevealThatASuspendedTenantExists` y `#hashesEvenWhenTheEmailDoesNotExist`.

## A08 Software and Data Integrity Failures

- Aplica: sí.
- Mitigación: outbox persistido en la misma transacción que el negocio;
  consumidores idempotentes con deduplicación por `(eventId, consumidor)`;
  el fallo de un consumidor propaga y reintenta en vez de darse por publicado;
  escaneo de secretos y de dependencias en CI.
- Evidencia: `OutboxGuaranteesIntegrationTest`,
  `JdbcProcessedEventStoreIntegrationTest`, `LoggingIntegrationEventPublisherTest`.
- Revisado en T160-02: hasta la Ola 2 el publicador se tragaba el fallo del
  consumidor y marcaba el mensaje como publicado, de modo que la garantía de
  reintento que documenta ADR-0012 no llegaba a aplicarse al correo. Corregido,
  y con ello la deduplicación pasó a ser obligatoria: sin ella el reintento
  duplicaba los efectos de los consumidores que sí habían terminado bien.

## A09 Security Logging and Monitoring Failures

- Aplica: sí.
- Mitigación: auditoría append-only de las operaciones administrativas y de
  plataforma, consultable por tenant y sin endpoints de modificación; logs
  estructurados con `correlationId`, `tenantId`, `userId` y caso de uso;
  métricas de login fallido y de cuentas bloqueadas; sondas de salud.
- Evidencia: `AuditEventControllerIntegrationTest`,
  `StructuredLoggingIntegrationTest`, `NoSecretsInLogsTest`,
  `HealthEndpointIntegrationTest`, `AccountLockoutIntegrationTest`.
- Revisado en T160-02: la prohibición de registrar secretos (RS-014) no depende
  de la disciplina de quien escribe el log. `ObservabilityContext` limita las
  claves del MDC —que es lo que el formateador vuelca— y `NoSecretsInLogsTest`
  rastrea los argumentos de toda llamada al logger, incluido el caso de un
  mensaje que anuncia el secreto con una variable de nombre neutro.
- Limitación asumida: no hay alertado automático sobre esas métricas. El panel
  técnico (T140-05) sigue sin implementar y está declarado como opcional (P3).

## A10 Server-Side Request Forgery

- Aplica: no de forma material en el MVP actual.
- Justificación: la aplicación no realiza llamadas salientes controladas por
  el usuario final.

## Hallazgos cerrados en esta iteración

- Respuesta 500 genérica sin filtrar stack trace/SQL.
- CORS restringido al origen configurado.
- Enumeración de usuarios evitada en `register`.
- Límite de tamaño de payload y de campos `reason`/`resolutionComment`.
- Test de rutas no públicas protegidas.
