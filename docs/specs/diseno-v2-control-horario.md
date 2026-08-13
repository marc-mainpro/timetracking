# Documento de Diseño — V2 del SaaS Multitenant de Control Horario

## 1. Propósito

Este documento describe el diseño técnico de la versión 2 de una aplicación SaaS multitenant de control horario.

El diseño parte del documento de requisitos V2 y define cómo se implementarán:

- La arquitectura general.
- Los módulos funcionales.
- El modelo de dominio.
- La persistencia.
- La seguridad.
- El multitenancy.
- La administración de tenants.
- Los calendarios laborales.
- Las ausencias.
- Los turnos.
- Los informes.
- Las notificaciones.
- La auditoría.
- El patrón Transactional Outbox.
- La observabilidad.
- El despliegue.
- La estrategia de testing.

La solución se mantendrá como un monolito modular y no incluirá microservicios, brokers de mensajería, alta disponibilidad, escalabilidad horizontal, MFA, SSO, facturación ni API pública para terceros.

---

# 2. Objetivos de diseño

El diseño debe garantizar:

- Aislamiento seguro entre tenants.
- Separación clara de responsabilidades.
- Dominio independiente de frameworks.
- Evolución incremental desde el MVP.
- Seguridad por defecto.
- Trazabilidad de operaciones.
- Pruebas automatizadas.
- Facilidad de despliegue.
- Facilidad de mantenimiento.
- Documentación sincronizada con el código.
- Preparación para procesos asíncronos internos sin infraestructura distribuida.

---

# 3. Arquitectura general

## 3.1 Vista de alto nivel

```text
┌─────────────────────────────┐
│        Angular SPA          │
│ Public / Tenant / Platform  │
└──────────────┬──────────────┘
               │ HTTPS / JSON
┌──────────────▼──────────────┐
│      Spring Boot API        │
│     Monolito modular        │
├─────────────────────────────┤
│ Identity and Access         │
│ Tenant Management           │
│ Time Tracking               │
│ Calendars                   │
│ Absences                    │
│ Shifts                      │
│ Corrections                 │
│ Reporting                   │
│ Notifications               │
│ Audit                       │
│ Integration Events          │
└──────────────┬──────────────┘
               │ JDBC / JPA
┌──────────────▼──────────────┐
│         PostgreSQL          │
│ Shared DB + Shared Schema   │
└─────────────────────────────┘
```

## 3.2 Estilo arquitectónico

Se utilizará:

- Monolito modular.
- Clean Architecture.
- DDD táctico.
- API REST.
- Persistencia relacional.
- Eventos de dominio locales.
- Eventos de integración versionados.
- Transactional Outbox.
- Procesamiento interno mediante tareas programadas.

## 3.3 Restricciones

No se introducirán:

- Microservicios.
- RabbitMQ.
- Kafka.
- Kubernetes.
- Event sourcing.
- CQRS completo.
- Alta disponibilidad.
- Escalabilidad horizontal.
- API pública para terceros.
- MFA.
- SSO.
- Facturación.

---

# 4. Organización del backend

## 4.1 Estructura por módulos

```text
backend/
└── src/main/java/com/example/timecontrol/
    ├── shared/
    │   ├── domain/
    │   ├── application/
    │   ├── infrastructure/
    │   └── interfaces/
    ├── identity/
    ├── tenant/
    ├── timetracking/
    ├── calendar/
    ├── absence/
    ├── shift/
    ├── correction/
    ├── reporting/
    ├── notification/
    ├── audit/
    └── integration/
```

Cada módulo seguirá una estructura interna equivalente:

```text
module/
├── domain/
│   ├── model/
│   ├── event/
│   ├── service/
│   └── repository/
├── application/
│   ├── command/
│   ├── query/
│   ├── usecase/
│   ├── dto/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── configuration/
│   └── adapter/
└── interfaces/
    └── rest/
```

## 4.2 Reglas de dependencia

- `domain` no depende de Spring.
- `application` depende de `domain`.
- `infrastructure` depende de `application` y `domain`.
- `interfaces` depende de `application`.
- Un módulo no accede directamente al repositorio de otro módulo.
- Las interacciones entre módulos se realizan mediante:
  - Puertos de aplicación.
  - Servicios expuestos explícitamente.
  - Eventos de dominio o integración.
- ArchUnit verificará estas reglas.

---

# 5. Módulos del sistema

## 5.1 Identity and Access

Responsabilidades:

- Autenticación.
- Gestión de credenciales.
- Refresh tokens.
- Gestión de sesiones.
- Bloqueo temporal.
- Recuperación de contraseña.
- Roles.
- Autorización.

Entidades principales:

- `UserAccount`
- `UserRole`
- `Session`
- `RefreshToken`
- `PasswordResetToken`

## 5.2 Tenant Management

Responsabilidades:

- Registro de tenants.
- Administración de plataforma.
- Ciclo de vida.
- Activación.
- Suspensión.
- Reactivación.
- Archivado.
- Configuración básica.

Entidades principales:

- `Tenant`
- `TenantRegistration`
- `TenantSettings`

## 5.3 Time Tracking

Responsabilidades:

- Jornadas.
- Pausas.
- Cálculo de horas.
- Detección de jornadas incompletas.
- Horas extra.
- Reglas de redondeo.
- Historial de cambios.

Entidades principales:

- `Workday`
- `BreakEntry`
- `WorkdayChange`

## 5.4 Calendar

Responsabilidades:

- Calendarios laborales.
- Festivos.
- Días laborables.
- Jornadas especiales.
- Asignación por tenant, equipo o empleado.

Entidades principales:

- `WorkCalendar`
- `CalendarDayRule`
- `Holiday`
- `CalendarAssignment`

## 5.5 Absence

Responsabilidades:

- Solicitudes de ausencia.
- Aprobación.
- Rechazo.
- Tipos de ausencia.
- Calendario de equipo.

Entidades principales:

- `AbsenceRequest`
- `AbsenceType`
- `AbsenceResolution`

## 5.6 Shift

Responsabilidades:

- Plantillas de turno.
- Asignación de turnos.
- Turnos nocturnos.
- Comparación entre previsto y registrado.

Entidades principales:

- `ShiftTemplate`
- `ShiftAssignment`

## 5.7 Correction

Responsabilidades:

- Solicitudes de corrección.
- Aprobación.
- Rechazo.
- Aplicación de cambios.
- Auditoría.

Entidades principales:

- `CorrectionRequest`
- `CorrectionResolution`

## 5.8 Reporting

Responsabilidades:

- Informes por empleado.
- Informes por equipo.
- Informes por periodo.
- Exportación CSV.
- Exportación PDF opcional.

Se implementará mediante servicios de consulta y proyecciones SQL optimizadas, sin introducir CQRS completo.

## 5.9 Notification

Responsabilidades:

- Notificaciones internas.
- Correos.
- Reintentos.
- Historial de envío.
- Plantillas.

Entidades principales:

- `Notification`
- `NotificationDelivery`

## 5.10 Audit

Responsabilidades:

- Auditoría de plataforma.
- Auditoría de tenant.
- Registro append-only.
- Consulta filtrada.

Entidad principal:

- `AuditEvent`

## 5.11 Integration Events

Responsabilidades:

- Transformar eventos de dominio.
- Persistir Outbox.
- Publicar internamente.
- Reintentar fallos.
- Registrar estado.

Entidades principales:

- `OutboxMessage`
- `ProcessedMessage`

---

# 6. Diseño multitenant

## 6.1 Estrategia

Se utilizará:

```text
Una base de datos
+ un esquema compartido
+ tenant_id en tablas de negocio
```

## 6.2 Resolución del tenant

El tenant se resolverá desde el usuario autenticado.

Flujo:

```text
Access token
→ SecurityContext
→ AuthenticatedPrincipal
→ tenantId
→ TenantContext
→ casos de uso y repositorios
```

No se confiará en:

- Cabeceras enviadas por el cliente.
- Parámetros de consulta.
- Identificadores de tenant en el cuerpo.

## 6.3 Repositorios tenant-aware

Todos los repositorios de negocio deberán incluir `tenantId`.

Ejemplo conceptual:

```java
Optional<Workday> findByIdAndTenantId(WorkdayId id, TenantId tenantId);
```

No se permitirán métodos genéricos como:

```java
findById(id)
```

para entidades tenant-scoped, salvo infraestructura interna justificada.

## 6.4 Restricciones de base de datos

Ejemplos:

```sql
UNIQUE (tenant_id, email)
UNIQUE (tenant_id, employee_code)
UNIQUE (tenant_id, calendar_name)
```

Índices:

```sql
CREATE INDEX idx_workday_tenant_employee_date
ON workday (tenant_id, employee_id, started_at);
```

## 6.5 Protección adicional

Se considerará opcionalmente PostgreSQL Row-Level Security como defensa adicional, pero no será obligatoria para la V2.

---

# 7. Diseño de administración de tenants

## 7.1 Estados

```text
PENDING
ACTIVE
SUSPENDED
ARCHIVED
```

## 7.2 Transiciones

```text
PENDING → ACTIVE
ACTIVE → SUSPENDED
SUSPENDED → ACTIVE
ACTIVE → ARCHIVED
SUSPENDED → ARCHIVED
```

No se permitirá:

```text
ARCHIVED → ACTIVE
```

salvo futura decisión registrada mediante ADR.

## 7.3 Registro público

El formulario público creará una solicitud de alta.

Flujo:

```text
POST /public/tenant-registrations
→ validar datos
→ aplicar rate limiting
→ crear TenantRegistration
→ opcionalmente verificar email
→ crear Tenant en PENDING
→ revisión o activación
```

## 7.4 Administración de plataforma

Rutas frontend:

```text
/platform/login
/platform/tenants
/platform/tenants/:tenantId
/platform/registrations
/platform/audit
```

Endpoints:

```text
GET  /api/v1/platform/tenants
GET  /api/v1/platform/tenants/{tenantId}
POST /api/v1/platform/tenants/{tenantId}/activate
POST /api/v1/platform/tenants/{tenantId}/suspend
POST /api/v1/platform/tenants/{tenantId}/reactivate
POST /api/v1/platform/tenants/{tenantId}/archive
```

## 7.5 Seguridad

Las acciones críticas requerirán:

- Rol `PLATFORM_ADMIN`.
- Motivo obligatorio.
- Reautenticación opcional.
- Auditoría.
- Protección CSRF.
- Correlation ID.

---

# 8. Diseño de autenticación y sesiones

## 8.1 Access token

- Duración corta.
- Contendrá:
  - `userId`
  - `tenantId`
  - roles
  - identificador de sesión
- Firmado por el backend.
- No se almacenará como token persistente en base de datos.

## 8.2 Refresh token

- Rotatorio.
- Almacenado en cookie:
  - `HttpOnly`
  - `Secure`
  - `SameSite`
- Persistido como hash.
- Asociado a una sesión.
- Revocable.

## 8.3 Sesiones

Entidad `Session`:

- `id`
- `userId`
- `tenantId`
- `createdAt`
- `lastUsedAt`
- `expiresAt`
- `revokedAt`
- `userAgentHash`
- `ipHash`

## 8.4 Recuperación de contraseña

Flujo:

```text
Solicitud
→ respuesta anti-enumeración
→ token aleatorio
→ almacenar hash
→ enviar correo
→ validar token
→ cambiar contraseña
→ revocar sesiones
```

## 8.5 Bloqueo temporal

Se registrarán:

- Intentos fallidos.
- Fecha del último intento.
- Fecha de desbloqueo.

El bloqueo será temporal y configurable.

---

# 9. Modelo de dominio

## 9.1 Tenant

```text
Tenant
- id
- name
- status
- timezone
- createdAt
- activatedAt
- suspendedAt
- archivedAt
- version
```

Métodos:

- `activate()`
- `suspend(reason)`
- `reactivate()`
- `archive(reason)`

Eventos:

- `TenantActivated`
- `TenantSuspended`
- `TenantReactivated`
- `TenantArchived`

## 9.2 Workday

```text
Workday
- id
- tenantId
- employeeId
- status
- startedAt
- endedAt
- breaks
- version
```

Métodos:

- `start()`
- `startBreak()`
- `endBreak()`
- `close()`
- `adjust()`

Eventos:

- `WorkdayStarted`
- `BreakStarted`
- `BreakEnded`
- `WorkdayClosed`
- `WorkdayAdjusted`

## 9.3 WorkCalendar

```text
WorkCalendar
- id
- tenantId
- name
- timezone
- validFrom
- validTo
- dayRules
- holidays
```

Responsabilidades:

- Determinar si un día es laborable.
- Obtener horas esperadas.
- Aplicar jornadas especiales.

## 9.4 AbsenceRequest

```text
AbsenceRequest
- id
- tenantId
- employeeId
- type
- startDate
- endDate
- reason
- status
- resolution
```

Estados:

- `PENDING`
- `APPROVED`
- `REJECTED`
- `CANCELLED`

Métodos:

- `approve()`
- `reject()`
- `cancel()`

## 9.5 ShiftTemplate

```text
ShiftTemplate
- id
- tenantId
- name
- startTime
- endTime
- breakPolicy
- crossesMidnight
```

## 9.6 ShiftAssignment

```text
ShiftAssignment
- id
- tenantId
- employeeId
- shiftTemplateId
- validFrom
- validTo
```

---

# 10. Persistencia

## 10.1 Tecnología

- PostgreSQL.
- Spring Data JPA.
- Flyway.
- Testcontainers.

## 10.2 Estrategia de mapeo

El dominio no utilizará anotaciones JPA.

Se podrán emplear modelos de persistencia separados cuando el agregado lo justifique.

Ejemplo:

```text
Workday
↔ WorkdayJpaEntity
↔ WorkdayMapper
```

Para entidades simples podrá valorarse un modelo compartido si no compromete el dominio, pero la opción preferida será separación explícita.

## 10.3 Tablas principales

```text
tenant
tenant_registration
tenant_settings
user_account
user_role
user_session
refresh_token
password_reset_token
workday
break_entry
workday_change
correction_request
work_calendar
calendar_day_rule
holiday
calendar_assignment
absence_request
absence_type
shift_template
shift_assignment
notification
notification_delivery
audit_event
outbox_message
processed_message
```

## 10.4 Borrado

Se evitará el borrado físico inmediato.

Se utilizarán:

- Estado.
- `archived_at`.
- `deleted_at` cuando proceda.

La eliminación definitiva se tratará como proceso administrativo documentado.

---

# 11. Diseño de API REST

## 11.1 Convenciones

- Base path: `/api/v1`.
- JSON.
- Problem Details.
- Paginación.
- Filtros.
- Ordenación.
- Fechas ISO 8601.
- Instantes UTC.
- Versionado en URL.

## 11.2 Áreas

```text
/api/v1/public/**
/api/v1/auth/**
/api/v1/app/**
/api/v1/admin/**
/api/v1/platform/**
```

## 11.3 Ejemplos

### Registro público

```text
POST /api/v1/public/tenant-registrations
POST /api/v1/public/tenant-registrations/verify-email
```

### Sesiones

```text
GET    /api/v1/auth/sessions
DELETE /api/v1/auth/sessions/{sessionId}
DELETE /api/v1/auth/sessions
```

### Calendarios

```text
GET    /api/v1/admin/calendars
POST   /api/v1/admin/calendars
GET    /api/v1/admin/calendars/{calendarId}
PUT    /api/v1/admin/calendars/{calendarId}
DELETE /api/v1/admin/calendars/{calendarId}
```

### Ausencias

```text
POST /api/v1/app/absences
GET  /api/v1/app/absences
GET  /api/v1/admin/absences
POST /api/v1/admin/absences/{absenceId}/approve
POST /api/v1/admin/absences/{absenceId}/reject
```

### Turnos

```text
GET  /api/v1/admin/shifts/templates
POST /api/v1/admin/shifts/templates
POST /api/v1/admin/shifts/assignments
GET  /api/v1/app/shifts
```

## 11.4 Errores

Formato:

```json
{
  "type": "about:blank",
  "title": "Tenant is suspended",
  "status": 403,
  "detail": "The tenant is currently suspended.",
  "errorCode": "TENANT_SUSPENDED",
  "correlationId": "uuid",
  "timestamp": "2026-07-24T10:00:00Z"
}
```

---

# 12. Eventos de dominio e integración

## 12.1 Eventos de dominio

Características:

- Inmutables.
- En pasado.
- Sin dependencia de Spring.
- Generados por agregados.
- Procesados de forma síncrona cuando afectan a la misma transacción.

Ejemplos:

- `TenantActivated`
- `WorkdayClosed`
- `CorrectionApproved`
- `AbsenceApproved`
- `ShiftAssigned`

## 12.2 Eventos de integración

Ejemplos:

```text
platform.tenant-activated.v1
time-tracking.workday-closed.v1
absence.absence-approved.v1
shift.shift-assigned.v1
```

Campos:

- `eventId`
- `eventType`
- `eventVersion`
- `tenantId`
- `aggregateId`
- `occurredAt`
- `payload`

## 12.3 Transformación

```text
Evento de dominio
→ handler de aplicación
→ evento de integración
→ Outbox
```

---

# 13. Transactional Outbox

## 13.1 Flujo

```text
Caso de uso
→ modificar agregado
→ guardar agregado
→ extraer eventos
→ crear evento de integración
→ guardar Outbox
→ commit
```

## 13.2 Publicación interna

Una tarea programada leerá mensajes pendientes.

```text
Outbox PENDING
→ PROCESSING
→ consumidor interno
→ PUBLISHED
```

En caso de fallo:

```text
PROCESSING
→ PENDING con next_attempt_at
→ FAILED al superar límite
```

## 13.3 Concurrencia

Se utilizará:

```sql
FOR UPDATE SKIP LOCKED
```

## 13.4 Idempotencia

Los consumidores internos registrarán `eventId` en `processed_message`.

## 13.5 Sin broker

El Outbox no enviará mensajes a RabbitMQ ni Kafka.

Consumidores previstos:

- Notificaciones.
- Auditoría derivada.
- Actualización de proyecciones.
- Procesos internos.

---

# 14. Notificaciones

## 14.1 Tipos

- Internas.
- Email.

## 14.2 Casos

- Jornada incompleta.
- Fichaje olvidado.
- Corrección aprobada.
- Corrección rechazada.
- Ausencia aprobada.
- Ausencia rechazada.

## 14.3 Flujo

```text
Evento de dominio
→ evento de integración
→ Outbox
→ NotificationHandler
→ NotificationDelivery
```

## 14.4 Estados

```text
PENDING
SENT
FAILED
CANCELLED
```

## 14.5 Reintentos

- Número máximo configurable.
- Backoff.
- Registro del último error.
- Reintento manual opcional.

---

# 15. Informes

## 15.1 Enfoque

Los informes se implementarán mediante:

- Consultas SQL optimizadas.
- Proyecciones DTO.
- Índices específicos.
- Servicios de aplicación de solo lectura.

No se implementará CQRS completo.

## 15.2 Informes

- Por empleado.
- Por equipo.
- Por periodo.
- Horas trabajadas.
- Pausas.
- Horas extra.
- Ausencias.
- Jornadas incompletas.
- Desviación previsto-real.

## 15.3 Exportación

- CSV obligatorio.
- PDF opcional.

Las exportaciones grandes podrán ejecutarse mediante proceso interno y almacenarse temporalmente.

---

# 16. Frontend Angular

## 16.1 Estructura

```text
src/app/
├── core/
│   ├── auth/
│   ├── guards/
│   ├── interceptors/
│   ├── error-handling/
│   └── services/
├── shared/
│   ├── components/
│   ├── directives/
│   ├── pipes/
│   └── models/
├── public/
├── platform/
├── tenant/
└── layout/
```

## 16.2 Áreas

### Pública

- Registro de tenant.
- Verificación de email.
- Login.
- Recuperación de contraseña.

### Tenant

- Dashboard.
- Jornada actual.
- Historial.
- Correcciones.
- Ausencias.
- Turnos.
- Notificaciones.

### Administración tenant

- Empleados.
- Calendarios.
- Ausencias.
- Turnos.
- Informes.
- Auditoría.

### Plataforma

- Tenants.
- Solicitudes de registro.
- Auditoría.
- Estado del sistema.

## 16.3 Guards

- `AuthGuard`
- `RoleGuard`
- `TenantStatusGuard`
- `PlatformAdminGuard`

## 16.4 Interceptors

- Access token.
- Correlation ID.
- Tratamiento de errores.
- Refresh controlado.

## 16.5 Estado

Se utilizarán servicios y señales de Angular.

No se introducirá una librería global de estado salvo necesidad demostrada.

---

# 17. Seguridad

## 17.1 Controles

- Hash seguro de contraseñas.
- Access token corto.
- Refresh token rotatorio.
- Cookie segura.
- CSRF.
- CORS restringido.
- Rate limiting.
- Bloqueo temporal.
- Validación de entrada.
- Autorización por rol.
- Autorización por tenant.
- Auditoría.
- Secret scanning.
- Dependency scanning.

## 17.2 Amenazas principales

- Acceso cruzado entre tenants.
- Escalada de privilegios.
- Creación masiva de tenants.
- Enumeración de usuarios.
- Robo de refresh token.
- Reutilización de tokens.
- Manipulación de identificadores.
- Inyección.
- XSS.
- CSRF.
- Exposición de secretos.
- Abuso de endpoints administrativos.

## 17.3 Mitigaciones

- Tenant desde principal autenticado.
- IDs opacos.
- Validación.
- Prepared statements.
- Cookies seguras.
- CSP.
- Auditoría.
- Rate limiting.
- Mensajes anti-enumeración.
- Revocación de sesión.

---

# 18. Auditoría

## 18.1 Tipos

- Plataforma.
- Tenant.

## 18.2 Datos

```text
AuditEvent
- id
- tenantId nullable
- actorUserId
- action
- entityType
- entityId
- beforeState
- afterState
- reason
- correlationId
- occurredAt
```

## 18.3 Reglas

- Append-only.
- Sin edición por API.
- Sin secretos.
- Consulta paginada.
- Filtros por fecha, actor, acción y entidad.

---

# 19. Observabilidad

## 19.1 Logs

Formato estructurado.

Campos:

- Timestamp.
- Level.
- Correlation ID.
- Tenant ID.
- User ID.
- Use case.
- Result.

## 19.2 Métricas

- Latencia.
- Errores.
- Peticiones.
- Logins fallidos.
- Cuentas bloqueadas.
- Outbox pendiente.
- Outbox fallido.
- Notificaciones fallidas.
- Jobs fallidos.

## 19.3 Health checks

- Aplicación.
- PostgreSQL.
- Outbox.
- Servicio de correo.

## 19.4 Panel técnico

Opcionalmente:

- Estado de jobs.
- Mensajes Outbox fallidos.
- Notificaciones fallidas.
- Último backup.
- Última restauración probada.

---

# 20. Backups y recuperación

## 20.1 Backups

- Backup periódico de PostgreSQL.
- Retención configurable.
- Almacenamiento protegido.
- Registro de ejecución.

## 20.2 Restauración

Debe existir un procedimiento documentado:

```text
detener aplicación
→ restaurar base
→ ejecutar validaciones
→ arrancar aplicación
→ ejecutar smoke tests
```

## 20.3 Validación

La restauración deberá probarse al menos una vez por versión relevante o periodo definido.

---

# 21. Testing

## 21.1 Unitario

Cobertura principal:

- Agregados.
- Objetos de valor.
- Políticas.
- Casos de uso.
- Eventos.
- Mappers.

## 21.2 Integración

Con Testcontainers:

- PostgreSQL.
- Flyway.
- Repositorios.
- Seguridad.
- Multitenancy.
- Outbox.
- Auditoría.
- Notificaciones.

## 21.3 Arquitectura

ArchUnit:

- Dominio sin Spring.
- Sin ciclos.
- Controladores sin repositorios.
- Módulos aislados.
- Outbox en infraestructura.

## 21.4 End-to-end

Flujos:

- Alta de tenant.
- Activación por plataforma.
- Login.
- Gestión de empleado.
- Jornada.
- Corrección.
- Calendario.
- Ausencia.
- Turno.
- Informe.
- Notificación.

## 21.5 Seguridad

- Cross-tenant.
- Escalada de privilegios.
- Tenant suspendido.
- Sesión revocada.
- Refresh token reutilizado.
- Rate limiting.
- CSRF.
- Enumeración.

---

# 22. Despliegue

## 22.1 Componentes

```text
Reverse Proxy
Angular
Spring Boot
PostgreSQL
Servicio SMTP
```

## 22.2 Docker Compose

Servicios:

- `frontend`
- `backend`
- `postgres`
- `reverse-proxy`
- `mailhog` en desarrollo

## 22.3 Configuración

Variables:

- Base de datos.
- JWT.
- Cookies.
- SMTP.
- Rate limiting.
- Registro público.
- Backups.
- Outbox.

## 22.4 Entornos

- Local.
- Test.
- Staging opcional.
- Producción.

---

# 23. Migraciones

## 23.1 Estrategia

Flyway.

## 23.2 Principios

- Migraciones versionadas.
- No modificar migraciones publicadas.
- Añadir nuevas migraciones.
- Backfills controlados.
- Estrategia expand-contract cuando sea necesaria.

## 23.3 Orden recomendado

1. Añadir tablas de administración de tenants.
2. Añadir sesiones y recuperación.
3. Añadir calendarios.
4. Añadir ausencias.
5. Añadir turnos.
6. Añadir notificaciones.
7. Añadir mejoras de auditoría.
8. Añadir índices de informes.

---

# 24. ADR requeridos

- Administración de plataforma separada.
- Registro público controlado.
- Ciclo de vida del tenant.
- Gestión de sesiones.
- Recuperación de contraseña.
- Calendarios laborales.
- Ausencias.
- Turnos.
- Informes mediante proyecciones SQL.
- Notificaciones mediante Outbox.
- Estrategia de backups.
- Retención de datos.
- Reautenticación de acciones críticas.
- Row-Level Security, si se adopta.

---

# 25. Riesgos de diseño

## Riesgo 1 — Crecimiento del monolito

Mitigación:

- Modularidad.
- ArchUnit.
- APIs internas.
- Sin acceso cruzado a repositorios.

## Riesgo 2 — Complejidad del dominio

Mitigación:

- Agregados pequeños.
- Políticas explícitas.
- Documentación de reglas.
- Tests.

## Riesgo 3 — Fugas entre tenants

Mitigación:

- TenantContext.
- Repositorios tenant-aware.
- Pruebas cross-tenant.
- Restricciones SQL.

## Riesgo 4 — Outbox innecesariamente complejo

Mitigación:

- Uso limitado.
- Consumidores internos.
- Sin broker.
- Estados simples.

## Riesgo 5 — Informes lentos

Mitigación:

- Índices.
- Paginación.
- Proyecciones SQL.
- Exportaciones asíncronas internas.

## Riesgo 6 — Exceso de alcance

Mitigación:

- Priorizar administración de tenants, seguridad, calendarios, ausencias e informes.
- Mantener PDF, roles adicionales y panel técnico como opcionales.

---

# 26. Orden de implementación recomendado

## Iteración 1 — Consolidación

- Refactor del MVP.
- Revisión de seguridad.
- Revisión multitenant.
- Cobertura.
- ADR.

## Iteración 2 — Administración de tenants

- `PLATFORM_ADMIN`.
- Estados.
- Panel.
- Endpoints.
- Auditoría.

## Iteración 3 — Seguridad

- Sesiones.
- Refresh tokens.
- Recuperación.
- Bloqueo.
- Rate limiting.

## Iteración 4 — Calendarios y reglas

- Calendarios.
- Festivos.
- Reglas horarias.
- Horas extra.
- Redondeo.

## Iteración 5 — Ausencias

- Solicitud.
- Aprobación.
- Calendario de equipo.

## Iteración 6 — Turnos

- Plantillas.
- Asignaciones.
- Nocturnos.
- Comparación.

## Iteración 7 — Informes

- Proyecciones.
- CSV.
- PDF opcional.

## Iteración 8 — Notificaciones

- Internas.
- Email.
- Reintentos.
- Outbox.

## Iteración 9 — Operación

- Logs.
- Métricas.
- Health.
- Backups.
- Restauración.

## Iteración 10 — Endurecimiento

- E2E.
- Seguridad.
- Documentación.
- CI.

---

# 27. Definition of Done de diseño

Un incremento se considerará completo cuando:

- Cumpla requisitos.
- Respete el diseño.
- Incluya pruebas.
- Mantenga aislamiento multitenant.
- Actualice OpenAPI.
- Actualice ADR.
- Actualice eventos.
- Actualice migraciones.
- Mantenga cobertura.
- Pase ArchUnit.
- Pase CI.
- No introduzca secretos.
- Actualice documentación.

---

# 28. Resultado esperado

La V2 deberá producir una aplicación:

- Segura.
- Multitenant.
- Administrable.
- Auditable.
- Modular.
- Testeada.
- Desplegable.
- Recuperable.
- Mantenible.
- Sin complejidad distribuida innecesaria.

Arquitectura final objetivo:

```text
Angular
+ Spring Boot
+ PostgreSQL
+ monolito modular
+ Clean Architecture
+ DDD
+ administración de tenants
+ sesiones seguras
+ calendarios
+ ausencias
+ turnos
+ informes
+ notificaciones
+ auditoría
+ Transactional Outbox
+ backups
+ observabilidad básica
```
