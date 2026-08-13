# Plan de tareas y dependencias — V2 del SaaS Multitenant de Control Horario

## 1. Propósito

Este documento define las tareas necesarias para implementar la V2 del SaaS multitenant de control horario.

Está diseñado para que un agente de IA pueda:

- Identificar el orden correcto de ejecución.
- Resolver dependencias entre tareas.
- Trabajar por iteraciones.
- Ejecutar tareas en paralelo cuando sea seguro.
- Validar criterios de entrada y salida.
- Mantener código, pruebas y documentación sincronizados.
- Evitar introducir funcionalidades fuera de alcance.

---

# 2. Alcance

La V2 incluirá:

- Consolidación del MVP.
- Administración segura de tenants.
- Gestión del ciclo de vida de tenants.
- Gestión de sesiones.
- Recuperación de contraseña.
- Protección del registro público.
- Reglas horarias.
- Calendarios laborales.
- Ausencias.
- Turnos básicos.
- Informes.
- Notificaciones internas y por correo.
- Auditoría.
- Transactional Outbox.
- Observabilidad básica.
- Backups y restauración.
- Testing y documentación.

Quedan fuera de alcance:

- MFA.
- SSO.
- API pública para terceros.
- RabbitMQ.
- Kafka.
- Facturación.
- Alta disponibilidad.
- Escalabilidad horizontal.
- Kubernetes.
- Microservicios.
- Event sourcing.
- CQRS completo.

---

# 3. Reglas de ejecución para el agente

El agente deberá:

1. Leer `AGENTS.md`.
2. Leer este documento.
3. Leer los ADR vigentes.
4. Revisar la documentación del módulo afectado.
5. Ejecutar únicamente tareas cuyos prerrequisitos estén completados.
6. No modificar decisiones arquitectónicas sin crear o actualizar un ADR.
7. Mantener el sistema como monolito modular.
8. Añadir pruebas para toda regla de negocio.
9. Añadir pruebas cross-tenant para toda funcionalidad tenant-scoped.
10. Actualizar OpenAPI cuando cambie la API.
11. Actualizar el catálogo de eventos cuando cambie un contrato.
12. Actualizar migraciones Flyway cuando cambie el modelo de datos.
13. No marcar una tarea como completada si CI falla.
14. No introducir dependencias sin justificación.
15. No confiar en `tenant_id` enviado por el cliente.
16. No incluir secretos en el repositorio.
17. Mantener trazabilidad entre requisitos, tareas y pruebas.

---

# 4. Estados de tarea

Cada tarea deberá tener uno de estos estados:

- `BLOCKED`: tiene dependencias pendientes.
- `READY`: todas sus dependencias están completadas.
- `IN_PROGRESS`: está en ejecución.
- `REVIEW`: implementación terminada y pendiente de validación.
- `DONE`: cumple todos los criterios.
- `CANCELLED`: retirada mediante decisión documentada.

---

# 5. Prioridades

- `P0`: bloqueante o crítica.
- `P1`: necesaria para completar la V2.
- `P2`: importante, pero puede posponerse.
- `P3`: opcional.

---

# 6. Grafo general de dependencias

```text
T00 Preparación
│
├── T10 Consolidación del MVP
│   ├── T20 Arquitectura modular
│   ├── T30 Seguridad base
│   └── T40 Infraestructura de testing
│
├── T50 Administración de tenants
│   ├── T51 Ciclo de vida
│   ├── T52 PLATFORM_ADMIN
│   ├── T53 Registro público seguro
│   └── T54 Panel de plataforma
│
├── T60 Sesiones y recuperación
│
├── T70 Calendarios y reglas horarias
│   ├── T71 Calendarios
│   ├── T72 Reglas horarias
│   └── T73 Cálculo de horas extra
│
├── T80 Ausencias
│
├── T90 Turnos
│
├── T100 Informes
│
├── T110 Notificaciones
│   └── depende de T120 Outbox
│
├── T120 Outbox y eventos
│
├── T130 Auditoría
│
├── T140 Observabilidad
│
├── T150 Backups y restauración
│
└── T160 Endurecimiento final
```

---

# 7. Épica T00 — Preparación del trabajo

## T00-01 — Revisar documentación existente

**Prioridad:** P0  
**Dependencias:** ninguna.

### Objetivo

Validar que existen y son coherentes:

- Documento de requisitos V2.
- Documento de diseño V2.
- ADR.
- `AGENTS.md`.
- Skills.
- OpenAPI.
- Modelo de dominio.
- Estrategia de testing.

### Entregables

- Informe de inconsistencias.
- Lista de documentos faltantes.
- Backlog de correcciones documentales.

### Criterios de finalización

- No existen contradicciones críticas.
- Las decisiones fuera de alcance están documentadas.
- Se identifican todas las tareas bloqueantes.

---

## T00-02 — Crear matriz de trazabilidad

**Prioridad:** P0  
**Dependencias:** T00-01.

### Objetivo

Relacionar:

```text
Requisito
→ tarea
→ caso de uso
→ endpoint
→ prueba
→ documentación
```

### Entregables

- `docs/traceability/requirements-matrix.md`.

### Criterios de finalización

- Todos los requisitos P0 y P1 tienen al menos una tarea.
- Toda tarea funcional referencia requisitos.

---

## T00-03 — Revisar AGENTS.md

**Prioridad:** P0  
**Dependencias:** T00-01.

### Objetivo

Actualizar las reglas del agente para la V2.

### Debe incluir

- Arquitectura modular.
- Reglas multitenant.
- Seguridad.
- Eventos.
- Outbox.
- Testing.
- Documentación.
- Restricciones de alcance.

### Criterios de finalización

- `AGENTS.md` refleja la V2.
- Las restricciones descartadas aparecen explícitamente.

---

## T00-04 — Crear o actualizar skills

**Prioridad:** P1  
**Dependencias:** T00-03.

### Skills mínimas

```text
.skills/create-use-case/SKILL.md
.skills/create-rest-endpoint/SKILL.md
.skills/create-database-migration/SKILL.md
.skills/review-multitenancy/SKILL.md
.skills/create-domain-event/SKILL.md
.skills/create-integration-event/SKILL.md
.skills/review-outbox/SKILL.md
.skills/update-documentation/SKILL.md
.skills/create-security-test/SKILL.md
```

### Criterios de finalización

- Cada skill define entradas, pasos, validaciones y salida.
- Cada skill indica qué documentación actualizar.

---

# 8. Épica T10 — Consolidación del MVP

## T10-01 — Ejecutar auditoría técnica del MVP

**Prioridad:** P0  
**Dependencias:** T00-01.

### Revisar

- Arquitectura.
- Seguridad.
- Multitenancy.
- Persistencia.
- Testing.
- Cobertura.
- Dependencias.
- Código duplicado.
- Documentación.
- Errores conocidos.

### Entregables

- `docs/reviews/mvp-technical-audit.md`.
- Backlog de deuda técnica.

### Criterios de finalización

- Los defectos se clasifican por severidad.
- Los bloqueantes se convierten en tareas.

---

## T10-02 — Corregir defectos críticos del MVP

**Prioridad:** P0  
**Dependencias:** T10-01.

### Incluye

- Fugas cross-tenant.
- Errores de autorización.
- Falta de transacciones.
- Invariantes rotas.
- Exposición de secretos.
- Errores de migración.
- Fallos de CI.

### Criterios de finalización

- No quedan defectos críticos conocidos.
- Todas las correcciones tienen pruebas.

---

## T10-03 — Actualizar dependencias

**Prioridad:** P1  
**Dependencias:** T10-01.

### Objetivo

Actualizar dependencias con vulnerabilidades o incompatibilidades.

### Criterios de finalización

- Build correcto.
- Tests correctos.
- Sin vulnerabilidades críticas conocidas.
- Cambios documentados.

---

## T10-04 — Normalizar gestión de errores

**Prioridad:** P1  
**Dependencias:** T10-02.

### Objetivo

Aplicar Problem Details de forma uniforme.

### Entregables

- Manejador global.
- Códigos de error.
- Pruebas.
- OpenAPI actualizado.

---

# 9. Épica T20 — Arquitectura modular

## T20-01 — Definir límites de módulos

**Prioridad:** P0  
**Dependencias:** T10-01.

### Módulos

- Identity.
- Tenant.
- Time Tracking.
- Calendar.
- Absence.
- Shift.
- Correction.
- Reporting.
- Notification.
- Audit.
- Integration.

### Entregables

- Diagrama de módulos.
- Dependencias permitidas.
- ADR actualizado.

---

## T20-02 — Implementar reglas ArchUnit

**Prioridad:** P0  
**Dependencias:** T20-01.

### Reglas

- Dominio sin Spring.
- Sin ciclos.
- Controladores sin repositorios.
- Infraestructura implementa puertos.
- Módulos sin acceso directo a repositorios ajenos.
- Outbox aislado en infraestructura.

### Criterios de finalización

- Las reglas se ejecutan en CI.
- Los incumplimientos existentes están corregidos o documentados.

---

## T20-03 — Refactorizar dependencias entre módulos

**Prioridad:** P1  
**Dependencias:** T20-02.

### Objetivo

Eliminar acoplamientos no permitidos.

### Criterios de finalización

- ArchUnit pasa.
- No existen dependencias circulares.
- Los contratos internos están documentados.

---

# 10. Épica T30 — Seguridad base

## T30-01 — Revisar autenticación actual

**Prioridad:** P0  
**Dependencias:** T10-01.

### Revisar

- Access tokens.
- Refresh tokens.
- Cookies.
- CSRF.
- CORS.
- Expiración.
- Revocación.
- Claims.
- Mensajes de error.

### Entregables

- Informe de seguridad.
- Lista de cambios.

---

## T30-02 — Endurecer configuración de cookies

**Prioridad:** P0  
**Dependencias:** T30-01.

### Requisitos

- `HttpOnly`.
- `Secure`.
- `SameSite`.
- Caducidad.
- Path limitado.
- Rotación.

---

## T30-03 — Implementar rate limiting

**Prioridad:** P1  
**Dependencias:** T30-01.

### Endpoints

- Login.
- Refresh.
- Recuperación de contraseña.
- Registro público.
- Reenvío de verificación.

### Criterios de finalización

- Límites configurables.
- Respuesta 429.
- Pruebas automatizadas.

---

## T30-04 — Implementar bloqueo temporal de cuentas

**Prioridad:** P1  
**Dependencias:** T30-01.

### Criterios

- Umbral configurable.
- Duración configurable.
- Auditoría.
- Reinicio tras autenticación correcta.
- Pruebas.

---

## T30-05 — Añadir escaneo de secretos y dependencias

**Prioridad:** P1  
**Dependencias:** T10-03.

### Entregables

- Job de CI.
- Política de severidad.
- Documentación de excepciones.

---

# 11. Épica T40 — Infraestructura de testing

## T40-01 — Consolidar Testcontainers

**Prioridad:** P0  
**Dependencias:** T10-02.

### Objetivo

Usar PostgreSQL real en integración.

### Criterios

- Configuración reutilizable.
- Migraciones Flyway incluidas.
- Tests reproducibles.

---

## T40-02 — Añadir tests cross-tenant base

**Prioridad:** P0  
**Dependencias:** T40-01.

### Casos

- Lectura cruzada.
- Escritura cruzada.
- Enumeración de IDs.
- Administración cruzada.

---

## T40-03 — Configurar umbrales de cobertura

**Prioridad:** P1  
**Dependencias:** T10-02.

### Umbrales

- Dominio: 90 %.
- Aplicación: 80 %.

### Criterios

- CI falla si se reducen por debajo del umbral.
- Exclusiones justificadas.

---

## T40-04 — Crear base E2E

**Prioridad:** P1  
**Dependencias:** T40-01.

### Flujo mínimo

- Registro.
- Activación.
- Login.
- Jornada.
- Corrección.

---

# 12. Épica T50 — Administración de tenants

## T50-01 — Modelar ciclo de vida del tenant

**Prioridad:** P0  
**Dependencias:** T20-01, T40-01.

### Estados

```text
PENDING
ACTIVE
SUSPENDED
ARCHIVED
```

### Métodos

- `activate`
- `suspend`
- `reactivate`
- `archive`

### Pruebas

- Transiciones válidas.
- Transiciones inválidas.
- Concurrencia.

---

## T50-02 — Crear migración de estados de tenant

**Prioridad:** P0  
**Dependencias:** T50-01.

### Criterios

- Migración compatible con datos existentes.
- Backfill documentado.
- Índices revisados.

---

## T50-03 — Implementar bloqueo por estado

**Prioridad:** P0  
**Dependencias:** T50-02, T30-01.

### Objetivo

Impedir operaciones a tenants suspendidos o archivados.

### Debe cubrir

- Login.
- Refresh.
- Casos de uso.
- Jobs.
- Notificaciones.

### Pruebas

- Tenant suspendido.
- Tenant archivado.
- Reautenticación tras reactivación.

---

## T50-04 — Crear rol PLATFORM_ADMIN

**Prioridad:** P0  
**Dependencias:** T30-01.

### Requisitos

- No asignable desde administración tenant.
- No disponible en registro público.
- Creación controlada.
- Auditoría.

---

## T50-05 — Implementar API de plataforma

**Prioridad:** P0  
**Dependencias:** T50-03, T50-04, T130-01.

### Endpoints

```text
GET  /api/v1/platform/tenants
GET  /api/v1/platform/tenants/{id}
POST /api/v1/platform/tenants/{id}/activate
POST /api/v1/platform/tenants/{id}/suspend
POST /api/v1/platform/tenants/{id}/reactivate
POST /api/v1/platform/tenants/{id}/archive
```

### Criterios

- Autorización.
- Motivo obligatorio.
- Auditoría.
- OpenAPI.
- Paginación.
- Filtros.

---

## T50-06 — Implementar panel Angular de plataforma

**Prioridad:** P1  
**Dependencias:** T50-05.

### Pantallas

- Login de plataforma.
- Lista de tenants.
- Detalle.
- Acciones de ciclo de vida.
- Auditoría.

### Pruebas

- Guards.
- Permisos.
- Manejo de errores.
- Estados de carga.

---

# 13. Épica T53 — Registro público seguro

## T53-01 — Modelar TenantRegistration

**Prioridad:** P0  
**Dependencias:** T50-01.

### Campos

- Empresa.
- Propietario.
- Email.
- Estado.
- Token de verificación.
- Fechas.
- Fuente.
- IP hash.

### Estados

- `PENDING_EMAIL_VERIFICATION`
- `PENDING_REVIEW`
- `APPROVED`
- `REJECTED`
- `EXPIRED`
- `CONSUMED`

---

## T53-02 — Crear migración TenantRegistration

**Prioridad:** P0  
**Dependencias:** T53-01.

---

## T53-03 — Separar solicitud y creación de tenant

**Prioridad:** P0  
**Dependencias:** T53-02.

### Flujo

```text
Solicitud
→ verificación
→ aprobación
→ creación transaccional de tenant y propietario
```

### Criterios

- No crear tenant activo directamente.
- Operación idempotente.
- Auditoría.
- Eventos.

---

## T53-04 — Añadir feature flag de registro público

**Prioridad:** P1  
**Dependencias:** T53-03.

### Criterios

- Configurable por entorno.
- Respuesta controlada cuando está deshabilitado.
- Documentación.

---

## T53-05 — Implementar verificación de correo

**Prioridad:** P2  
**Dependencias:** T53-03, T110-01.

### Requisitos

- Token aleatorio.
- Hash almacenado.
- Caducidad.
- Un solo uso.
- Reenvío limitado.
- Respuesta anti-enumeración.

---

# 14. Épica T60 — Sesiones y recuperación de contraseña

## T60-01 — Modelar Session

**Prioridad:** P0  
**Dependencias:** T30-01.

### Campos

- Usuario.
- Tenant.
- Creación.
- Último uso.
- Expiración.
- Revocación.
- Hash de agente.
- Hash de IP.

---

## T60-02 — Crear migración de sesiones

**Prioridad:** P0  
**Dependencias:** T60-01.

---

## T60-03 — Implementar rotación de refresh token

**Prioridad:** P0  
**Dependencias:** T60-02, T30-02.

### Casos

- Refresh válido.
- Token reutilizado.
- Token revocado.
- Sesión expirada.
- Tenant suspendido.

---

## T60-04 — Implementar gestión de sesiones

**Prioridad:** P1  
**Dependencias:** T60-03.

### Endpoints

```text
GET    /api/v1/auth/sessions
DELETE /api/v1/auth/sessions/{id}
DELETE /api/v1/auth/sessions
```

---

## T60-05 — Modelar recuperación de contraseña

**Prioridad:** P1  
**Dependencias:** T60-01.

### Elementos

- Token.
- Hash.
- Caducidad.
- Uso único.

---

## T60-06 — Implementar recuperación de contraseña

**Prioridad:** P1  
**Dependencias:** T60-05, T110-01.

### Flujo

```text
Solicitud
→ respuesta anti-enumeración
→ correo
→ validación
→ nueva contraseña
→ revocar sesiones
```

---

# 15. Épica T70 — Calendarios y reglas horarias

## T70-01 — Modelar WorkCalendar

**Prioridad:** P0  
**Dependencias:** T20-01, T40-01.

### Incluye

- Nombre.
- Zona horaria.
- Vigencia.
- Reglas semanales.
- Festivos.
- Jornadas especiales.

---

## T70-02 — Modelar asignaciones de calendario

**Prioridad:** P1  
**Dependencias:** T70-01.

### Ámbitos

- Tenant.
- Equipo.
- Empleado.

### Regla

La asignación más específica prevalece.

---

## T70-03 — Crear migraciones de calendario

**Prioridad:** P0  
**Dependencias:** T70-01, T70-02.

---

## T70-04 — Implementar casos de uso de calendario

**Prioridad:** P1  
**Dependencias:** T70-03.

### Casos

- Crear.
- Editar.
- Archivar.
- Asignar.
- Consultar.
- Resolver calendario efectivo.

---

## T70-05 — Implementar API y frontend de calendarios

**Prioridad:** P1  
**Dependencias:** T70-04.

---

## T72-01 — Modelar reglas horarias

**Prioridad:** P0  
**Dependencias:** T70-01.

### Reglas

- Jornada máxima.
- Descanso obligatorio.
- Redondeo.
- Tolerancias.
- Horas esperadas.

---

## T72-02 — Implementar motor de evaluación de jornada

**Prioridad:** P0  
**Dependencias:** T72-01, T70-04.

### Salida

- Tiempo esperado.
- Tiempo real.
- Pausas.
- Horas extra.
- Incidencias.
- Desviaciones.

---

## T72-03 — Integrar reglas con Workday

**Prioridad:** P0  
**Dependencias:** T72-02.

### Criterios

- No romper invariantes existentes.
- Añadir eventos de anomalía cuando corresponda.
- Pruebas de horario de verano.

---

# 16. Épica T80 — Ausencias

## T80-01 — Modelar AbsenceType

**Prioridad:** P1  
**Dependencias:** T20-01.

### Incluye

- Código.
- Nombre.
- Requiere aprobación.
- Permite adjunto, opcional.
- Activo.

---

## T80-02 — Modelar AbsenceRequest

**Prioridad:** P0  
**Dependencias:** T80-01, T70-04.

### Estados

- `PENDING`
- `APPROVED`
- `REJECTED`
- `CANCELLED`

### Reglas

- Rango válido.
- Sin solapamientos incompatibles.
- Tenant correcto.
- Resolución única.

---

## T80-03 — Crear migraciones de ausencias

**Prioridad:** P0  
**Dependencias:** T80-02.

---

## T80-04 — Implementar casos de uso de ausencias

**Prioridad:** P0  
**Dependencias:** T80-03, T130-01.

### Casos

- Solicitar.
- Cancelar.
- Aprobar.
- Rechazar.
- Listar.
- Consultar calendario de equipo.

---

## T80-05 — Implementar API y frontend de ausencias

**Prioridad:** P1  
**Dependencias:** T80-04.

---

## T80-06 — Integrar ausencias con cálculo horario

**Prioridad:** P1  
**Dependencias:** T80-04, T72-02.

### Objetivo

Ajustar tiempo esperado según ausencias aprobadas.

---

# 17. Épica T90 — Turnos básicos

## T90-01 — Modelar ShiftTemplate

**Prioridad:** P1  
**Dependencias:** T20-01.

### Incluye

- Hora inicio.
- Hora fin.
- Cruce de medianoche.
- Pausas previstas.
- Estado.

---

## T90-02 — Modelar ShiftAssignment

**Prioridad:** P1  
**Dependencias:** T90-01.

### Reglas

- Vigencia.
- Sin solapamientos incompatibles.
- Tenant correcto.

---

## T90-03 — Crear migraciones de turnos

**Prioridad:** P1  
**Dependencias:** T90-02.

---

## T90-04 — Implementar casos de uso de turnos

**Prioridad:** P1  
**Dependencias:** T90-03.

---

## T90-05 — Implementar API y frontend de turnos

**Prioridad:** P2  
**Dependencias:** T90-04.

---

## T90-06 — Integrar turnos con evaluación de jornada

**Prioridad:** P1  
**Dependencias:** T90-04, T72-02.

---

# 18. Épica T100 — Informes

## T100-01 — Definir consultas y DTO de informes

**Prioridad:** P1  
**Dependencias:** T72-02, T80-06.

### Informes

- Empleado.
- Equipo.
- Periodo.
- Horas.
- Pausas.
- Extras.
- Ausencias.
- Incompletas.
- Previsto-real.

---

## T100-02 — Crear índices de reporting

**Prioridad:** P1  
**Dependencias:** T100-01.

### Criterios

- Analizar planes de ejecución.
- Documentar índices.
- Evitar duplicación innecesaria.

---

## T100-03 — Implementar servicios de consulta

**Prioridad:** P1  
**Dependencias:** T100-02.

### Restricciones

- Sin CQRS completo.
- DTO de solo lectura.
- Paginación.
- Tenant-scoped.

---

## T100-04 — Implementar exportación CSV

**Prioridad:** P1  
**Dependencias:** T100-03.

---

## T100-05 — Implementar frontend de informes

**Prioridad:** P1  
**Dependencias:** T100-03.

---

## T100-06 — Implementar PDF

**Prioridad:** P3  
**Dependencias:** T100-03.

---

# 19. Épica T110 — Notificaciones

## T110-01 — Definir puerto de correo

**Prioridad:** P1  
**Dependencias:** T20-01.

### Entregables

- Puerto.
- Adaptador SMTP.
- Adaptador fake para tests.

---

## T110-02 — Modelar Notification

**Prioridad:** P1  
**Dependencias:** T120-01.

### Estados

- `PENDING`
- `SENT`
- `FAILED`
- `CANCELLED`

---

## T110-03 — Crear migraciones de notificaciones

**Prioridad:** P1  
**Dependencias:** T110-02.

---

## T110-04 — Implementar consumidor interno de notificaciones

**Prioridad:** P1  
**Dependencias:** T110-01, T110-03, T120-05.

### Eventos

- Jornada incompleta.
- Corrección aprobada.
- Corrección rechazada.
- Ausencia aprobada.
- Ausencia rechazada.

---

## T110-05 — Implementar reintentos de notificación

**Prioridad:** P1  
**Dependencias:** T110-04.

---

## T110-06 — Implementar notificaciones internas

**Prioridad:** P2  
**Dependencias:** T110-03.

---

## T110-07 — Implementar UI de notificaciones

**Prioridad:** P2  
**Dependencias:** T110-06.

---

# 20. Épica T120 — Eventos y Transactional Outbox

## T120-01 — Definir contratos base de eventos

**Prioridad:** P0  
**Dependencias:** T20-01.

### Interfaces

- `DomainEvent`
- `IntegrationEvent`
- `EventId`
- `EventMetadata`

---

## T120-02 — Crear catálogo de eventos

**Prioridad:** P1  
**Dependencias:** T120-01.

### Documento

- Nombre.
- Versión.
- Productor.
- Consumidor.
- Payload.
- Sensibilidad.
- Compatibilidad.

---

## T120-03 — Modelar OutboxMessage

**Prioridad:** P0  
**Dependencias:** T120-01.

### Estados

- `PENDING`
- `PROCESSING`
- `PUBLISHED`
- `FAILED`

---

## T120-04 — Crear migración Outbox

**Prioridad:** P0  
**Dependencias:** T120-03.

### Índices

- Estado.
- Próximo intento.
- Fecha.
- Tenant.

---

## T120-05 — Implementar escritura transaccional

**Prioridad:** P0  
**Dependencias:** T120-04.

### Criterios

- Cambio de negocio y Outbox en la misma transacción.
- Rollback conjunto.
- Pruebas de fallo.

---

## T120-06 — Implementar publicador por polling

**Prioridad:** P0  
**Dependencias:** T120-05.

### Requisitos

- Lotes.
- `FOR UPDATE SKIP LOCKED`.
- Reintentos.
- Backoff.
- Estado FAILED.
- Métricas.

---

## T120-07 — Implementar idempotencia

**Prioridad:** P0  
**Dependencias:** T120-06.

### Entidad

- `ProcessedMessage`.

### Pruebas

- Evento duplicado.
- Fallo después de procesar.
- Reintento.

---

# 21. Épica T130 — Auditoría

## T130-01 — Consolidar modelo AuditEvent

**Prioridad:** P0  
**Dependencias:** T20-01.

### Campos

- Actor.
- Tenant.
- Acción.
- Entidad.
- Estado anterior.
- Estado posterior.
- Motivo.
- Correlation ID.
- Fecha.

---

## T130-02 — Crear migración de auditoría V2

**Prioridad:** P0  
**Dependencias:** T130-01.

---

## T130-03 — Implementar auditoría de plataforma

**Prioridad:** P0  
**Dependencias:** T130-02, T50-04.

---

## T130-04 — Implementar auditoría tenant

**Prioridad:** P1  
**Dependencias:** T130-02.

### Operaciones

- Usuarios.
- Jornadas.
- Correcciones.
- Calendarios.
- Ausencias.
- Turnos.

---

## T130-05 — Implementar consulta de auditoría

**Prioridad:** P1  
**Dependencias:** T130-03, T130-04.

### Requisitos

- Paginación.
- Filtros.
- Autorización.
- Sin edición.

---

# 22. Épica T140 — Observabilidad

## T140-01 — Estandarizar logs estructurados

**Prioridad:** P1  
**Dependencias:** T10-04.

### Campos

- Correlation ID.
- Tenant ID.
- User ID.
- Caso de uso.
- Resultado.

---

## T140-02 — Implementar correlation ID

**Prioridad:** P1  
**Dependencias:** T140-01.

### Criterios

- Generar si falta.
- Propagar.
- Devolver en respuesta.
- Incluir en auditoría.

---

## T140-03 — Añadir health checks

**Prioridad:** P1  
**Dependencias:** T120-06, T110-01.

### Checks

- Aplicación.
- PostgreSQL.
- Outbox.
- SMTP.

---

## T140-04 — Añadir métricas básicas

**Prioridad:** P1  
**Dependencias:** T140-01.

### Métricas

- Peticiones.
- Errores.
- Latencia.
- Login fallido.
- Cuentas bloqueadas.
- Outbox.
- Notificaciones.
- Jobs.

---

## T140-05 — Crear panel técnico básico

**Prioridad:** P3  
**Dependencias:** T140-03, T140-04.

---

# 23. Épica T150 — Backups y restauración

## T150-01 — Diseñar estrategia de backup

**Prioridad:** P1  
**Dependencias:** T10-01.

### Definir

- Frecuencia.
- Retención.
- Ubicación.
- Cifrado.
- Validación.
- Responsables.

---

## T150-02 — Automatizar backup

**Prioridad:** P1  
**Dependencias:** T150-01.

### Criterios

- Ejecución reproducible.
- Logs.
- Código de salida.
- Documentación.

---

## T150-03 — Crear procedimiento de restauración

**Prioridad:** P1  
**Dependencias:** T150-02.

---

## T150-04 — Ejecutar prueba de restauración

**Prioridad:** P1  
**Dependencias:** T150-03.

### Evidencias

- Fecha.
- Backup usado.
- Tiempo.
- Resultado.
- Incidencias.
- Smoke tests.

---

# 24. Épica T160 — Endurecimiento final

## T160-01 — Completar pruebas E2E

**Prioridad:** P0  
**Dependencias:** T50-06, T60-06, T70-05, T80-05, T90-05, T100-05, T110-07.

### Flujos

- Registro y activación.
- Login y sesiones.
- Tenant suspendido.
- Jornada.
- Corrección.
- Calendario.
- Ausencia.
- Turno.
- Informe.
- Notificación.

---

## T160-02 — Ejecutar revisión de seguridad

**Prioridad:** P0  
**Dependencias:** T30-05, T160-01.

### Revisar

- OWASP.
- Cross-tenant.
- CSRF.
- CORS.
- Tokens.
- Enumeración.
- Rate limiting.
- Privilegios.
- Auditoría.

---

## T160-03 — Revisar rendimiento

**Prioridad:** P1  
**Dependencias:** T100-03, T140-04.

### Escenarios

- Login.
- Fichaje.
- Listado de jornadas.
- Informes.
- Outbox.
- Notificaciones.

---

## T160-04 — Cerrar documentación

**Prioridad:** P0  
**Dependencias:** todas las tareas P0 y P1.

### Actualizar

- README.
- OpenAPI.
- ADR.
- Diagramas.
- Catálogo de eventos.
- Modelo de amenazas.
- Estrategia de testing.
- Backups.
- Despliegue.
- Manuales.

---

## T160-05 — Validar criterios globales

**Prioridad:** P0  
**Dependencias:** T160-01, T160-02, T160-04, T150-04.

### Resultado

- Checklist final.
- Evidencias.
- Riesgos pendientes.
- Deuda aceptada.
- Estado de CI.

---

# 25. Tareas paralelizables

Pueden ejecutarse en paralelo cuando sus dependencias estén resueltas:

## Grupo A

- T20-01 — Límites de módulos.
- T30-01 — Revisión de autenticación.
- T40-01 — Testcontainers.
- T130-01 — Auditoría.

## Grupo B

Después de consolidación:

- T50-01 — Ciclo de vida tenant.
- T60-01 — Sesiones.
- T70-01 — Calendarios.
- T80-01 — Tipos de ausencia.
- T90-01 — Turnos.
- T120-01 — Contratos de eventos.

## Grupo C

Después de modelos y migraciones:

- API de plataforma.
- API de calendarios.
- API de ausencias.
- API de turnos.
- Informes.
- Notificaciones.

No ejecutar en paralelo tareas que modifiquen:

- La misma migración.
- El mismo agregado.
- El mismo contrato de API.
- El mismo evento de integración.
- La misma política de seguridad.

---

# 26. Ruta crítica

La ruta mínima para completar la V2 es:

```text
T00-01
→ T10-01
→ T10-02
→ T20-01
→ T20-02
→ T40-01
→ T50-01
→ T50-02
→ T50-03
→ T50-04
→ T130-01
→ T130-02
→ T50-05
→ T53-01
→ T53-03
→ T60-01
→ T60-03
→ T70-01
→ T70-04
→ T72-01
→ T72-02
→ T80-02
→ T80-04
→ T100-01
→ T100-03
→ T120-01
→ T120-05
→ T120-06
→ T110-04
→ T150-04
→ T160-01
→ T160-02
→ T160-04
→ T160-05
```

---

# 27. Definition of Ready

Una tarea está lista cuando:

- Sus dependencias están en `DONE`.
- Los requisitos están identificados.
- El diseño está disponible.
- Los criterios de aceptación están definidos.
- Las entidades afectadas están identificadas.
- Los riesgos se conocen.
- No existe una decisión arquitectónica pendiente.
- Se conocen los tests requeridos.
- Se conocen los documentos que deben actualizarse.

---

# 28. Definition of Done

Una tarea está completada cuando:

- El código está implementado.
- Las pruebas unitarias pasan.
- Las pruebas de integración pasan.
- Las pruebas cross-tenant pasan cuando aplican.
- ArchUnit pasa.
- La cobertura no disminuye.
- OpenAPI está actualizado.
- Las migraciones están añadidas y probadas.
- Los ADR están actualizados.
- El catálogo de eventos está actualizado.
- La documentación está sincronizada.
- No hay secretos.
- No hay vulnerabilidades críticas conocidas.
- CI está en verde.
- La tarea tiene evidencia de validación.

---

# 29. Formato de ejecución por tarea

El agente deberá registrar cada tarea con este formato:

```markdown
## Tarea: TXX-YY — Nombre

### Estado
READY | IN_PROGRESS | REVIEW | DONE | BLOCKED

### Requisitos
- RF-...
- RNF-...
- RS-...

### Dependencias
- TXX-YY

### Cambios realizados
- ...

### Archivos modificados
- ...

### Migraciones
- ...

### Pruebas
- Unitarias:
- Integración:
- Seguridad:
- E2E:

### Cobertura
- Antes:
- Después:

### Documentación
- OpenAPI:
- ADR:
- Eventos:
- README:
- Otros:

### Riesgos
- ...

### Resultado
- ...
```

---

# 30. Orden recomendado para el agente

El agente deberá:

1. Ejecutar tareas P0.
2. Completar dependencias.
3. Mantener lotes pequeños.
4. No mezclar épicas en un mismo cambio salvo dependencia directa.
5. Ejecutar pruebas tras cada tarea.
6. Actualizar documentación en el mismo cambio.
7. Registrar riesgos.
8. Detener la tarea si detecta una contradicción arquitectónica.
9. Crear ADR antes de introducir una decisión nueva.
10. Continuar con la siguiente tarea `READY`.

---

# 31. Criterio de finalización de la V2

La V2 se considerará completada cuando:

- Todas las tareas P0 estén en `DONE`.
- Todas las tareas P1 obligatorias estén en `DONE`.
- Las tareas P2 pendientes estén documentadas.
- La administración de tenants funcione.
- El registro público esté protegido.
- Las sesiones sean revocables.
- La recuperación de contraseña funcione.
- Los calendarios y reglas horarias estén operativos.
- Las ausencias estén operativas.
- Los turnos básicos estén operativos.
- Los informes estén operativos.
- Las notificaciones funcionen mediante Outbox.
- La auditoría sea completa.
- Los backups y restauración estén probados.
- Las pruebas cross-tenant pasen.
- La revisión de seguridad esté cerrada.
- La documentación esté actualizada.
- CI esté en verde.
