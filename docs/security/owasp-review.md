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
  frontend e imágenes Docker.
- Evidencia: `backend/pom.xml`, `frontend/package.json`, `.github/workflows/ci.yml`.
- Riesgo aceptado: no se añade aún un escáner automatizado dedicado tipo
  Dependency-Check por coste de pipeline; queda mitigado parcialmente por
  builds limpios y revisión manual de dependencias del MVP.

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
