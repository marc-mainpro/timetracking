# SDD — Desarrollo del MVP SaaS Multitenant de Control Horario

## 1. Propósito

Este documento define la especificación de desarrollo dirigida por especificaciones, SDD, para construir el MVP de una aplicación SaaS multitenant de control horario.

El documento está diseñado para ser utilizado por un agente de desarrollo autónomo o asistido por IA.

El agente deberá implementar el sistema siguiendo estrictamente:

- Clean Architecture.
- Domain-Driven Design.
- Seguridad desde el diseño.
- SOLID.
- KISS.
- YAGNI.
- DRY.
- Testing automatizado.
- Documentación viva.
- Trazabilidad de decisiones.
- Uso de eventos de dominio.
- Uso de Transactional Outbox.
- Revisión humana de todo código generado.

---

# 2. Objetivo del sistema

Construir una aplicación web SaaS multitenant que permita a distintas organizaciones gestionar el control horario de sus empleados.

El sistema deberá permitir:

- Registrar organizaciones.
- Gestionar usuarios y roles.
- Registrar entradas, pausas y salidas.
- Consultar jornadas.
- Solicitar y aprobar correcciones.
- Consultar informes básicos.
- Auditar operaciones sensibles.
- Generar eventos de dominio.
- Persistir eventos de integración mediante Transactional Outbox.
- Preparar la aplicación para futura escalabilidad sin introducir microservicios en el MVP.

---

# 3. Stack tecnológico obligatorio

## Backend

- Java 21.
- Spring Boot.
- Spring Security.
- Spring Data JPA.
- Bean Validation.
- PostgreSQL.
- Flyway.
- OpenAPI.
- JUnit 5.
- AssertJ.
- Mockito.
- Testcontainers.
- ArchUnit.
- JaCoCo.

## Frontend

- Angular.
- TypeScript.
- Angular Router.
- Guards.
- Interceptors.
- Reactive Forms.
- Testing con las herramientas estándar del ecosistema Angular.

## Infraestructura

- Docker.
- Docker Compose.
- PostgreSQL.
- CI.
- Variables de entorno.
- No incluir secretos en el repositorio.

---

# 4. Principios arquitectónicos

## 4.1 Monolito modular

El sistema se implementará como un monolito modular.

Módulos mínimos:

- Identity and Access.
- Tenant Management.
- Time Tracking.
- Correction Management.
- Reporting.
- Audit.
- Integration Events.

## 4.2 Clean Architecture

Las dependencias deberán apuntar hacia el dominio.

Estructura orientativa:

```text
backend/
├── domain/
│   ├── model/
│   ├── valueobject/
│   ├── event/
│   ├── service/
│   └── repository/
├── application/
│   ├── usecase/
│   ├── command/
│   ├── query/
│   ├── dto/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── security/
│   ├── audit/
│   ├── outbox/
│   └── configuration/
└── interfaces/
    └── rest/
```

Reglas obligatorias:

- El dominio no depende de Spring.
- Los controladores no contienen lógica de negocio.
- Los controladores no acceden directamente a repositorios.
- La infraestructura implementa puertos definidos por el dominio o aplicación.
- No se permiten dependencias circulares.
- Las reglas deberán verificarse mediante ArchUnit.

---

# 5. Reglas multitenant

El MVP utilizará:

- Una base de datos compartida.
- Un esquema compartido.
- Columna `tenant_id` en todas las tablas de negocio que lo requieran.

Reglas obligatorias:

- El tenant se obtiene del usuario autenticado.
- No se confiará en un tenant enviado por el frontend.
- Toda consulta de negocio deberá estar filtrada por tenant.
- Toda restricción única relevante deberá incluir `tenant_id`.
- Toda operación administrativa deberá comprobar rol y tenant.
- Deben existir pruebas automáticas de acceso cruzado.

Ejemplo de restricción:

```text
UNIQUE (tenant_id, email)
```

---

# 6. Roles

## TENANT_ADMIN

Permisos:

- Gestionar empleados.
- Activar y desactivar usuarios.
- Asignar roles.
- Consultar jornadas del tenant.
- Revisar solicitudes de corrección.
- Aprobar o rechazar correcciones.
- Consultar informes.
- Consultar auditoría cuando corresponda.

## EMPLOYEE

Permisos:

- Consultar su perfil.
- Iniciar y finalizar jornada.
- Iniciar y finalizar pausas.
- Consultar su historial.
- Solicitar correcciones.

---

# 7. Entidades y agregados

## 7.1 Tenant

Campos mínimos:

- `id`
- `name`
- `status`
- `timezone`
- `createdAt`
- `updatedAt`

Reglas:

- El nombre es obligatorio.
- La zona horaria debe ser IANA.
- Un tenant inactivo no podrá operar.

## 7.2 User

Campos mínimos:

- `id`
- `tenantId`
- `email`
- `passwordHash`
- `firstName`
- `lastName`
- `status`
- `roles`
- `createdAt`
- `updatedAt`

Reglas:

- El email debe ser único dentro del tenant.
- Un usuario inactivo no puede autenticarse.
- Un usuario pertenece a un único tenant en el MVP.

## 7.3 Workday

Campos mínimos:

- `id`
- `tenantId`
- `employeeId`
- `status`
- `startedAt`
- `endedAt`
- `version`
- `createdAt`
- `updatedAt`

Estados:

- `OPEN`
- `ON_BREAK`
- `CLOSED`
- `ADJUSTED`

Reglas:

- Solo puede existir una jornada abierta por empleado.
- No se puede iniciar una pausa sin jornada activa.
- No se puede iniciar una segunda pausa.
- No se puede finalizar una jornada con una pausa abierta.
- No se puede cerrar una jornada ya cerrada.
- La hora normal de fichaje será la hora del servidor.
- Los cambios históricos se realizarán mediante correcciones.

## 7.4 BreakEntry

Campos mínimos:

- `id`
- `workdayId`
- `startedAt`
- `endedAt`

Reglas:

- Una pausa debe pertenecer a una jornada.
- No puede finalizar antes de comenzar.
- Solo puede existir una pausa abierta por jornada.

## 7.5 CorrectionRequest

Campos mínimos:

- `id`
- `tenantId`
- `workdayId`
- `requestedBy`
- `reason`
- `proposedChanges`
- `status`
- `resolvedBy`
- `resolvedAt`
- `resolutionComment`
- `createdAt`

Estados:

- `PENDING`
- `APPROVED`
- `REJECTED`

Reglas:

- Solo una solicitud pendiente por jornada y usuario, salvo justificación.
- Una solicitud resuelta no puede resolverse de nuevo.
- Toda aprobación debe generar auditoría.
- Toda aprobación debe actualizar la jornada de forma controlada.

---

# 8. Casos de uso obligatorios

## Autenticación

- `RegisterTenant`
- `AuthenticateUser`
- `RefreshSession`
- `LogoutUser`

## Usuarios

- `CreateEmployee`
- `UpdateEmployee`
- `DeactivateEmployee`
- `ActivateEmployee`
- `AssignRole`
- `ListEmployees`
- `GetEmployee`

## Jornadas

- `StartWorkday`
- `StartBreak`
- `EndBreak`
- `EndWorkday`
- `GetCurrentWorkday`
- `ListOwnWorkdays`
- `ListTenantWorkdays`
- `GetWorkday`

## Correcciones

- `RequestWorkdayCorrection`
- `ApproveCorrectionRequest`
- `RejectCorrectionRequest`
- `ListCorrectionRequests`
- `GetCorrectionRequest`

## Informes

- `GenerateEmployeeTimeSummary`
- `GenerateTenantTimeSummary`
- `ExportTimeSummaryCsv`

## Integración

- `PublishPendingOutboxMessages`
- `RetryFailedOutboxMessage`
- `ArchivePublishedOutboxMessages`

---

# 9. API REST mínima

## Autenticación

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

## Usuarios

```text
GET    /api/v1/employees
POST   /api/v1/employees
GET    /api/v1/employees/{employeeId}
PUT    /api/v1/employees/{employeeId}
PATCH  /api/v1/employees/{employeeId}/activate
PATCH  /api/v1/employees/{employeeId}/deactivate
PUT    /api/v1/employees/{employeeId}/roles
```

## Jornadas

```text
GET  /api/v1/workdays/current
POST /api/v1/workdays/start
POST /api/v1/workdays/current/breaks/start
POST /api/v1/workdays/current/breaks/end
POST /api/v1/workdays/current/end
GET  /api/v1/workdays
GET  /api/v1/workdays/{workdayId}
```

## Administración de jornadas

```text
GET /api/v1/admin/workdays
GET /api/v1/admin/workdays/{workdayId}
```

## Correcciones

```text
POST /api/v1/workdays/{workdayId}/corrections
GET  /api/v1/corrections
GET  /api/v1/corrections/{correctionId}
POST /api/v1/corrections/{correctionId}/approve
POST /api/v1/corrections/{correctionId}/reject
```

## Informes

```text
GET /api/v1/reports/employees/{employeeId}/summary
GET /api/v1/reports/tenant/summary
GET /api/v1/reports/tenant/export.csv
```

---

# 10. Gestión de errores

La API deberá utilizar Problem Details.

Campos mínimos:

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

Reglas:

- No exponer stack traces.
- No exponer información interna.
- Usar códigos de error estables.
- Incluir errores de validación por campo.

---

# 11. Seguridad

Requisitos obligatorios:

- Password hashing seguro.
- Access token de corta duración.
- Refresh token rotatorio.
- Refresh token en cookie `HttpOnly`, `Secure`, `SameSite`.
- Protección CSRF cuando corresponda.
- CORS restringido.
- Validación de entradas.
- Autorización por rol.
- Autorización por tenant.
- Rate limiting en autenticación.
- Cabeceras de seguridad.
- Secretos mediante variables de entorno.
- No registrar tokens o contraseñas.
- Auditoría de acciones críticas.

Pruebas obligatorias:

- Usuario anónimo no accede a recursos privados.
- Empleado no accede a endpoints de administrador.
- Usuario de tenant A no accede a tenant B.
- Usuario inactivo no inicia sesión.
- Token inválido no concede acceso.
- Refresh token reutilizado se invalida.

---

# 12. Eventos de dominio

Eventos mínimos:

- `TenantRegistered`
- `EmployeeCreated`
- `EmployeeDeactivated`
- `WorkdayStarted`
- `BreakStarted`
- `BreakEnded`
- `WorkdayClosed`
- `CorrectionRequested`
- `CorrectionApproved`
- `CorrectionRejected`

Reglas:

- Deben representar hechos ya ocurridos.
- Deben ser inmutables.
- No deben depender de Spring.
- No deben incluir entidades JPA.
- Deben contener únicamente datos necesarios.
- Deben generarse dentro de agregados cuando corresponda.
- No se usarán para validar invariantes internas.

---

# 13. Eventos de integración

Los eventos de dominio relevantes se transformarán en eventos de integración.

Ejemplos:

- `tenant.registered.v1`
- `identity.employee-created.v1`
- `time-tracking.workday-closed.v1`
- `corrections.correction-approved.v1`

Campos mínimos:

```json
{
  "eventId": "uuid",
  "eventType": "time-tracking.workday-closed.v1",
  "eventVersion": 1,
  "occurredAt": "2026-07-17T12:00:00Z",
  "tenantId": "uuid",
  "aggregateId": "uuid",
  "payload": {}
}
```

Reglas:

- Todos los eventos estarán versionados.
- Todo evento tendrá `eventId`.
- No se publicarán modelos internos directamente.
- El contrato se documentará en el catálogo de eventos.

---

# 14. Transactional Outbox

## 14.1 Requisito

El cambio de negocio y el registro Outbox deberán persistirse en la misma transacción.

## 14.2 Tabla mínima

```sql
CREATE TABLE outbox_message (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error TEXT,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

Estados:

- `PENDING`
- `PROCESSING`
- `PUBLISHED`
- `FAILED`

## 14.3 Publicador

Implementar polling con Spring Scheduler.

Requisitos:

- Procesamiento por lotes.
- Uso de `FOR UPDATE SKIP LOCKED`.
- Reintentos.
- Backoff.
- Registro de fallos.
- Estado `FAILED`.
- Métricas básicas.
- No introducir RabbitMQ o Kafka en el MVP salvo ADR aprobado.

## 14.4 Garantía

La entrega será `at-least-once`.

Los consumidores deberán ser idempotentes.

---

# 15. Auditoría

Tabla orientativa:

```text
audit_event
```

Campos mínimos:

- `id`
- `tenantId`
- `actorUserId`
- `action`
- `entityType`
- `entityId`
- `correlationId`
- `metadata`
- `occurredAt`

Reglas:

- Append-only.
- No editable mediante API.
- No almacenar secretos.
- Registrar acciones críticas.
- Toda aprobación de corrección debe quedar auditada.

---

# 16. Gestión temporal

Reglas:

- Almacenar instantes en UTC.
- Cada tenant tendrá zona horaria IANA.
- Los límites de día se calcularán en la zona del tenant.
- La hora normal de fichaje será la hora del servidor.
- Incluir pruebas de cambio horario estacional.

---

# 17. Concurrencia

Requisitos:

- Usar bloqueo optimista en `Workday`.
- Usar versión en entidades críticas.
- Gestionar conflictos con HTTP 409.
- Probar peticiones simultáneas.
- Impedir doble cierre de jornada.
- Impedir doble aprobación de corrección.
- Impedir doble procesamiento Outbox.

---

# 18. Frontend mínimo

Pantallas:

- Registro de organización.
- Login.
- Dashboard de empleado.
- Jornada actual.
- Historial.
- Solicitud de corrección.
- Gestión de empleados.
- Revisión de correcciones.
- Informes básicos.

Requisitos:

- Guards por autenticación.
- Guards por rol.
- Interceptor de autenticación.
- Manejo global de errores.
- Formularios reactivos.
- Validación cliente y servidor.
- Estados de carga.
- Mensajes de error claros.
- No almacenar refresh tokens en `localStorage`.

---

# 19. Testing

## Unitario

Cobertura mínima orientativa:

- Dominio: 90 %.
- Aplicación: 80 %.

Obligatorio probar:

- Invariantes de `Workday`.
- Transiciones de estado.
- Correcciones.
- Eventos de dominio.
- Transformación a eventos de integración.

## Integración

Usar Testcontainers con PostgreSQL.

Obligatorio probar:

- Repositorios.
- Flyway.
- Seguridad.
- Multitenancy.
- Controladores.
- Outbox.
- Reintentos.
- Atomicidad.

## Arquitectura

ArchUnit deberá verificar:

- Dominio independiente de Spring.
- Dependencias dirigidas hacia dentro.
- Sin acceso directo de controladores a repositorios.
- Sin ciclos.
- Outbox aislado en infraestructura.

## End-to-end

Flujo mínimo:

1. Registrar tenant.
2. Crear administrador.
3. Crear empleado.
4. Autenticar empleado.
5. Iniciar jornada.
6. Iniciar pausa.
7. Finalizar pausa.
8. Finalizar jornada.
9. Consultar historial.
10. Solicitar corrección.
11. Aprobar corrección.
12. Verificar auditoría.
13. Verificar Outbox.

---

# 20. Documentación obligatoria

Mantener:

```text
docs/
├── architecture/
├── adr/
├── domain/
├── api/
├── integration/
├── security/
├── testing/
└── ai/
```

Documentos mínimos:

- Visión.
- Contexto.
- Contenedores.
- Componentes.
- Glosario.
- Agregados.
- Reglas de negocio.
- OpenAPI.
- Catálogo de eventos.
- Modelo de amenazas.
- Estrategia de testing.
- Informe de cobertura.
- Política de uso de IA.
- ADR.

---

# 21. AGENTS.md

El repositorio deberá contener un `AGENTS.md` con estas reglas mínimas:

```markdown
# AGENTS.md

## Project objective

Build a secure multitenant time-tracking SaaS using Spring Boot,
Angular and PostgreSQL.

## Architecture

- Domain must not depend on Spring.
- Controllers must not contain business logic.
- Controllers must not access repositories directly.
- Infrastructure implements ports.
- Keep the MVP as a modular monolith.

## Multitenancy

- Never trust tenant IDs from clients.
- Resolve tenant from the authenticated principal.
- Every query must be tenant-scoped.
- Add cross-tenant tests.

## Events

- Domain events represent past facts.
- Domain events must not depend on Spring.
- Separate domain and integration events.
- Version integration contracts.
- Do not use events for aggregate invariants.

## Outbox

- Persist Outbox messages in the business transaction.
- Assume at-least-once delivery.
- Consumers must be idempotent.
- Add retry and atomicity tests.
- Do not add a broker without an ADR.

## Security

- Never log secrets.
- Validate all inputs.
- Authorize by role and tenant.
- Add security tests.

## Testing

- Add tests for every business rule.
- Use Testcontainers for PostgreSQL integration tests.
- Do not reduce configured coverage.

## Documentation

- Update OpenAPI when endpoints change.
- Update ADRs when decisions change.
- Update the event catalog when contracts change.
- Keep documentation synchronized.

## AI-generated code

- Review all generated code.
- Never commit generated code without tests.
- Verify dependencies and APIs.
- Prefer small, auditable changes.
```

---

# 22. Skills requeridas

Crear:

```text
.skills/create-use-case/SKILL.md
.skills/create-rest-endpoint/SKILL.md
.skills/create-database-migration/SKILL.md
.skills/review-multitenancy/SKILL.md
.skills/create-domain-event/SKILL.md
.skills/create-integration-event/SKILL.md
.skills/review-outbox/SKILL.md
.skills/update-documentation/SKILL.md
```

Cada skill deberá incluir:

- Objetivo.
- Entradas.
- Pasos.
- Validaciones.
- Pruebas.
- Criterios de finalización.
- Archivos que puede modificar.
- Archivos que debe actualizar.

---

# 23. Orden de implementación

El agente deberá seguir este orden:

## Iteración 1

- Inicializar repositorio.
- Crear backend.
- Crear frontend.
- Docker Compose.
- PostgreSQL.
- Flyway.
- CI.
- AGENTS.md.
- Estructura de documentación.
- ADR iniciales.

## Iteración 2

- Modelar Tenant.
- Modelar User.
- Registro de organización.
- Autenticación.
- Roles.
- Pruebas de seguridad.

## Iteración 3

- Implementar multitenancy.
- TenantContext.
- Repositorios tenant-aware.
- Pruebas cross-tenant.

## Iteración 4

- Modelar Workday.
- Implementar registro horario.
- Eventos de dominio.
- Pruebas unitarias.

## Iteración 5

- Gestión de empleados.
- Endpoints administrativos.
- Frontend administrativo.

## Iteración 6

- Correcciones.
- Auditoría.
- Concurrencia.
- Pruebas.

## Iteración 7

- Eventos de integración.
- Outbox.
- Publicador.
- Reintentos.
- Idempotencia.
- Pruebas transaccionales.

## Iteración 8

- Informes.
- CSV.
- Frontend de informes.

## Iteración 9

- End-to-end.
- Hardening.
- Revisión OWASP.
- Cobertura.
- Documentación final.

## Iteración 10

- Dockerización final.
- Despliegue.
- Manuales.
- Preparación de demo.

---

# 24. Criterios de aceptación globales

El MVP se considerará completado cuando:

- Dos tenants operan sin compartir datos.
- Un empleado completa una jornada con pausa.
- Se rechazan transiciones inválidas.
- Un administrador gestiona empleados.
- Una corrección se solicita, aprueba y audita.
- La API está documentada.
- Las migraciones son reproducibles.
- Las pruebas de seguridad pasan.
- Las pruebas cross-tenant pasan.
- La cobertura cumple los umbrales.
- La arquitectura cumple ArchUnit.
- Los eventos de dominio están documentados.
- Los eventos de integración están versionados.
- El cambio de negocio y el Outbox son atómicos.
- Los reintentos no producen efectos duplicados.
- El sistema arranca con Docker Compose.
- La documentación está actualizada.
- No existen secretos en el repositorio.
- El pipeline está en verde.

---

# 25. Definition of Done

Una tarea solo podrá marcarse como finalizada si:

- Cumple la especificación.
- Incluye pruebas.
- Pasa análisis estático.
- Mantiene cobertura.
- Respeta arquitectura.
- Respeta multitenancy.
- Respeta seguridad.
- Actualiza OpenAPI.
- Actualiza ADR cuando proceda.
- Actualiza catálogo de eventos.
- Actualiza documentación.
- No introduce dependencias innecesarias.
- No incluye secretos.
- Pasa CI.

---

# 26. Restricciones para el agente

El agente no deberá:

- Introducir microservicios.
- Introducir Kafka o RabbitMQ sin ADR.
- Introducir CQRS completo sin justificación.
- Introducir event sourcing.
- Introducir dependencias sin necesidad.
- Confiar en `tenant_id` enviado por el cliente.
- Mezclar entidades JPA con contratos API.
- Publicar entidades JPA como eventos.
- Implementar lógica de negocio en controladores.
- Reducir cobertura.
- Omitir pruebas por limitaciones de tiempo.
- Modificar ADR aceptados sin registrar una nueva decisión.
- Generar código no documentado.
- Marcar una tarea como finalizada con CI fallando.

---

# 27. Salida esperada del agente

Para cada iteración, el agente deberá entregar:

1. Código implementado.
2. Pruebas.
3. Migraciones.
4. Documentación actualizada.
5. ADR nuevos o modificados.
6. Resumen de cambios.
7. Riesgos detectados.
8. Decisiones pendientes.
9. Evidencia de CI.
10. Métricas de cobertura.

Formato mínimo del resumen:

```markdown
## Iteración completada

### Cambios
- ...

### Pruebas
- ...

### Cobertura
- ...

### Seguridad
- ...

### Documentación
- ...

### ADR
- ...

### Riesgos
- ...

### Pendientes
- ...
```

---

# 28. Resultado esperado

El resultado final deberá ser una aplicación:

- Segura.
- Multitenant.
- Modular.
- Testeada.
- Documentada.
- Desplegable.
- Auditable.
- Preparada para escalar.
- Compatible con futura mensajería.
- Desarrollada mediante un proceso SDD trazable.
