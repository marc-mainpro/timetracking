# CONTEXT-GLOBAL — Reglas transversales (obligatorio para toda tarea)

Fuente de verdad: `SDD-MVP-control-horario.md`. Este fichero condensa lo que toda tarea debe cumplir. Ante conflicto, gana el SDD.

## 1. Qué se construye

MVP SaaS **multitenant** de control horario: organizaciones (tenants) gestionan fichajes de sus empleados (entrada, pausas, salida), correcciones aprobables, informes básicos, auditoría y eventos vía Transactional Outbox. **Monolito modular** — prohibido introducir microservicios, Kafka/RabbitMQ, CQRS completo o event sourcing.

## 2. Stack fijado

- **Backend**: Java 21, Spring Boot 3.x, Spring Security, Spring Data JPA, Bean Validation, PostgreSQL, Flyway, springdoc-openapi, JUnit 5, AssertJ, Mockito, Testcontainers, ArchUnit, JaCoCo.
- **Frontend**: Angular (standalone components), TypeScript, Router + Guards + Interceptors, Reactive Forms.
- **Infra**: Docker, Docker Compose, GitHub Actions (CI). Secretos SOLO por variables de entorno.

## 3. Decisiones fijadas (no re-decidir; se documentan como ADR en T105)

| Tema | Decisión |
|---|---|
| Build backend | Maven, proyecto único `backend/` |
| Paquete base | `com.tfp.timetracking` |
| Módulos (paquetes) | `identity`, `tenant`, `timetracking`, `corrections`, `reporting`, `audit`, `outbox`, `shared` |
| Capas por módulo | `domain` / `application` / `infrastructure` / `interfaces.rest` |
| Auth | JWT firmado (HS256, secreto por env) vía Spring Security oauth2-resource-server; access token 15 min |
| Refresh token | Opaco, hasheado (SHA-256) en tabla `refresh_token`, rotatorio con detección de reutilización; cookie `HttpOnly; Secure; SameSite=Strict`, path `/api/v1/auth` |
| Passwords | BCrypt (`DelegatingPasswordEncoder` por defecto de Spring) |
| Rate limiting login | Bucket4j en memoria (10 req/min por IP) |
| Migraciones | Flyway `V<N>__descripcion.sql` en `backend/src/main/resources/db/migration` |
| IDs | UUID v4 generados en aplicación |
| Fechas | `Instant` (UTC) en persistencia; zona IANA del tenant solo para cálculo de límites de día |
| Errores | RFC 7807 Problem Details (ver §7) |
| Frontend estado auth | Access token en memoria (servicio), NUNCA refresh token en `localStorage` |
| CI | GitHub Actions: build + tests backend (con Testcontainers), build + tests frontend, verificación cobertura |

## 4. Clean Architecture (verificado por ArchUnit — romperlo = tarea fallida)

- `domain` NO importa Spring, JPA ni nada de `application`/`infrastructure`/`interfaces`.
- Controladores: sin lógica de negocio, sin acceso directo a repositorios; solo delegan en casos de uso.
- `infrastructure` implementa puertos (interfaces) definidos en `domain`/`application`.
- Sin ciclos de dependencia. Entidades JPA separadas del modelo de dominio y de los DTO de API.
- Eventos de dominio: hechos pasados, inmutables, sin Spring, sin entidades JPA.

## 5. Multitenancy (crítico)

- `tenant_id` en toda tabla de negocio; toda unique relevante incluye `tenant_id` (p. ej. `UNIQUE (tenant_id, email)`).
- El tenant SIEMPRE se resuelve del usuario autenticado (`TenantContext`), NUNCA de un parámetro del cliente.
- Toda query de negocio filtra por tenant. Toda operación admin comprueba rol Y tenant.
- Toda tarea que toque datos añade/mantiene tests de acceso cruzado entre tenants.

## 6. Seguridad

- Roles: `TENANT_ADMIN` (gestiona empleados, revisa correcciones, informes, auditoría) y `EMPLOYEE` (su jornada, su historial, sus correcciones).
- Validar toda entrada (Bean Validation en DTOs). CORS restringido por configuración. Cabeceras de seguridad activas.
- No loguear tokens ni contraseñas. Sin secretos en el repositorio (usar `.env.example`).
- Usuario inactivo no se autentica; tenant inactivo no opera.

## 7. Formato de error (todas las respuestas de error de la API)

```json
{
  "type": "about:blank",
  "title": "Invalid workday transition",
  "status": 409,
  "detail": "A workday cannot be closed while a break is open.",
  "errorCode": "WORKDAY_OPEN_BREAK",
  "correlationId": "uuid",
  "timestamp": "2026-07-17T12:00:00Z"
}
```

Sin stack traces ni detalles internos. `errorCode` estable y documentado. Errores de validación incluyen detalle por campo (`errors: [{field, message}]`). Conflictos de negocio/concurrencia → HTTP 409.

## 8. Testing

- Unitario: dominio ≥90 %, aplicación ≥80 % (JaCoCo lo verifica en build).
- Integración: Testcontainers PostgreSQL (repositorios, Flyway, seguridad, multitenancy, controladores, outbox). **Convención de nombre: `*IntegrationTest.java`** (Surefire NO ejecuta `*IT.java`: no hay Failsafe configurado; un test mal nombrado no se ejecuta nunca).
- Arquitectura: ArchUnit según §4.
- Prohibido bajar umbrales de cobertura u omitir tests "por tiempo".

## 9. Definition of Done (checklist por tarea)

1. Cumple la especificación de su ficha, sin ampliar alcance.
2. Incluye pruebas de toda regla de negocio tocada.
3. `mvn verify` (y `ng test`/`ng build` si toca frontend) en verde, cobertura mantenida.
4. Respeta arquitectura (ArchUnit verde), multitenancy y seguridad.
5. OpenAPI actualizado si cambió la API; catálogo de eventos actualizado si cambió un contrato; ADR nuevo si tomó una decisión no fijada aquí.
6. Documentación en `docs/` sincronizada. Sin dependencias innecesarias ni secretos.

## 10. Salida de tarea (informe obligatorio)

Escribir `tasks/_reports/TXXX-report.md`:

```markdown
## TXXX — <título>
### Cambios
### Pruebas (comandos ejecutados y resultado)
### Cobertura
### Seguridad
### Documentación actualizada
### ADR
### Riesgos detectados
### Pendientes / decisiones que necesitan humano
```
