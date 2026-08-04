# Revisión OWASP MVP

## Resumen

Checklist aplicado sobre el backend, frontend, `docker compose` y CI. La
revisión se valida con `mvn -B verify`, `npm run test:coverage`, pruebas de
seguridad específicas y análisis manual del código y configuración.

## A01 Broken Access Control

- Aplica: sí.
- Mitigación: Spring Security exige autenticación por defecto y `@PreAuthorize`
  por rol en endpoints de negocio.
- Evidencia: `RouteAuthorizationIntegrationTest`,
  `CrossTenantSecurityIntegrationTest`, suites REST de empleados, workdays,
  correcciones, auditoría e informes.

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
  transaccional, auditoría append-only, E2E de API del flujo completo.
- Evidencia: ADR-0005, ADR-0008, ADR-0009, `EndToEndFlowIT`.

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
- Mitigación: login uniforme, rate limiting, rotación de refresh token,
  revocación por reutilización, invalidación por usuario/tenant inactivo.
- Evidencia: `AuthSecurityIntegrationTest`, `AuthControllerIntegrationTest`.

## A08 Software and Data Integrity Failures

- Aplica: sí.
- Mitigación: outbox persistido en la misma transacción que el negocio;
  consumidores idempotentes y pruebas de atomicidad.
- Evidencia: `OutboxGuaranteesIntegrationTest`, pruebas T702/T703.

## A09 Security Logging and Monitoring Failures

- Aplica: sí.
- Mitigación: auditoría de aprobaciones/rechazos de correcciones, logs sin
  contraseñas ni tokens, `correlationId` propagado por request.
- Evidencia: `AuditEventControllerIntegrationTest`,
  `AuthControllerIntegrationTest`, `AuthSecurityIntegrationTest`.

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
