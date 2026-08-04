# Documento de Requisitos — V2 del SaaS Multitenant de Control Horario

## 1. Propósito

Este documento define los requisitos funcionales, no funcionales, de seguridad, operación y calidad para la versión 2 de una aplicación SaaS multitenant de control horario.

La V2 tiene como objetivo evolucionar el MVP hacia un producto más completo, administrable, seguro, auditable y preparado para usuarios reales, manteniendo una arquitectura simple y evitando complejidad distribuida innecesaria.

## 2. Alcance

La V2 incluirá:

- Administración segura de tenants.
- Gestión del ciclo de vida de tenants.
- Seguridad reforzada.
- Gestión de usuarios y roles.
- Reglas horarias configurables.
- Calendarios laborales.
- Gestión de ausencias.
- Gestión básica de turnos.
- Informes avanzados.
- Notificaciones internas y por correo.
- Auditoría.
- Transactional Outbox.
- Observabilidad básica.
- Backups y recuperación.
- Mejora de testing, documentación y mantenibilidad.

La V2 no incluirá:

- MFA.
- SSO.
- API pública para terceros.
- RabbitMQ.
- Kafka.
- Facturación SaaS.
- Alta disponibilidad.
- Escalabilidad horizontal.
- Kubernetes.
- Multi-región.
- Microservicios.
- Event sourcing.
- CQRS completo.

## 3. Contexto del sistema

La aplicación es un SaaS multitenant donde cada organización dispone de sus propios usuarios, empleados, jornadas, calendarios, ausencias, turnos e informes.

Arquitectura objetivo:

```text
Frontend Angular
        ↓
API REST Spring Boot
        ↓
Monolito modular
        ↓
PostgreSQL
```

Con:

- Clean Architecture.
- DDD táctico.
- Multitenancy mediante esquema compartido.
- Eventos de dominio.
- Eventos de integración.
- Transactional Outbox.
- Seguridad basada en autenticación, autorización y aislamiento por tenant.
- Testing automatizado.
- Documentación viva.

## 4. Actores

### 4.1 PLATFORM_ADMIN

Administrador global de la plataforma.

Responsabilidades:

- Gestionar tenants.
- Activar, suspender, reactivar y archivar tenants.
- Revisar solicitudes de alta.
- Consultar auditoría de plataforma.
- Consultar métricas básicas de uso.
- Gestionar el ciclo de vida de los tenants.

### 4.2 TENANT_ADMIN

Administrador de una organización.

Responsabilidades:

- Gestionar empleados.
- Gestionar roles.
- Configurar calendarios.
- Configurar reglas horarias.
- Gestionar ausencias.
- Gestionar turnos.
- Revisar correcciones.
- Consultar informes.
- Consultar auditoría del tenant.

### 4.3 EMPLOYEE

Empleado de una organización.

Responsabilidades:

- Registrar jornada.
- Registrar pausas.
- Consultar historial.
- Solicitar correcciones.
- Solicitar ausencias.
- Consultar turnos y calendario.

### 4.4 TEAM_MANAGER

Rol opcional.

Responsabilidades:

- Consultar empleados de su equipo.
- Aprobar ausencias.
- Consultar jornadas del equipo.
- Consultar informes limitados.

### 4.5 AUDITOR

Rol opcional.

Responsabilidades:

- Consultar auditoría.
- Consultar jornadas.
- Consultar informes.
- No modificar datos operativos.

## 5. Requisitos funcionales

### 5.1 Administración de tenants

#### RF-TEN-001 — Listado de tenants

El sistema deberá permitir al `PLATFORM_ADMIN` listar todos los tenants.

El listado deberá incluir:

- Nombre.
- Estado.
- Fecha de creación.
- Número de usuarios.
- Último acceso.
- Zona horaria.
- Fecha de activación.
- Fecha de suspensión, si aplica.

#### RF-TEN-002 — Consulta de tenant

El sistema deberá permitir consultar el detalle de un tenant.

#### RF-TEN-003 — Creación controlada de tenant

El sistema deberá permitir crear tenants mediante:

- Registro público controlado.
- Creación manual por `PLATFORM_ADMIN`.

#### RF-TEN-004 — Ciclo de vida del tenant

El sistema deberá soportar los estados:

- `PENDING`
- `ACTIVE`
- `SUSPENDED`
- `ARCHIVED`

#### RF-TEN-005 — Activación de tenant

El `PLATFORM_ADMIN` deberá poder activar un tenant pendiente.

#### RF-TEN-006 — Suspensión de tenant

El `PLATFORM_ADMIN` deberá poder suspender un tenant indicando un motivo.

#### RF-TEN-007 — Reactivación de tenant

El `PLATFORM_ADMIN` deberá poder reactivar un tenant suspendido.

#### RF-TEN-008 — Archivado de tenant

El `PLATFORM_ADMIN` deberá poder archivar un tenant.

#### RF-TEN-009 — Bloqueo de acceso por estado

Un tenant suspendido o archivado no deberá permitir acceso operativo a sus usuarios.

#### RF-TEN-010 — Desactivación del registro público

El sistema deberá permitir deshabilitar el registro público mediante configuración.

### 5.2 Registro público de tenants

#### RF-REG-001 — Solicitud de alta

El sistema deberá permitir enviar una solicitud de alta de tenant desde el frontend público.

#### RF-REG-002 — Validación de datos

La solicitud deberá validar:

- Nombre de empresa.
- Nombre y apellidos del propietario.
- Email.
- Contraseña.
- Aceptación de términos.

#### RF-REG-003 — Protección contra abuso

El sistema deberá aplicar:

- Rate limiting.
- Validación anti-bot si se habilita.
- Límite de solicitudes por IP.
- Límite de solicitudes por correo.

#### RF-REG-004 — Verificación de correo

El sistema podrá requerir verificación de correo antes de activar el tenant.

#### RF-REG-005 — Mensajes anti-enumeración

El sistema no deberá revelar si un correo ya está registrado.

#### RF-REG-006 — Auditoría del registro

Cada solicitud de alta deberá quedar auditada.

### 5.3 Gestión de usuarios

#### RF-USR-001 — Crear empleado

El `TENANT_ADMIN` deberá poder crear empleados dentro de su tenant.

#### RF-USR-002 — Editar empleado

El `TENANT_ADMIN` deberá poder modificar datos básicos de un empleado.

#### RF-USR-003 — Activar y desactivar usuarios

El `TENANT_ADMIN` deberá poder activar y desactivar usuarios.

#### RF-USR-004 — Asignar roles

El `TENANT_ADMIN` deberá poder asignar roles permitidos dentro de su tenant.

#### RF-USR-005 — Restricción por tenant

Un administrador no deberá poder gestionar usuarios de otro tenant.

#### RF-USR-006 — Recuperación de contraseña

El sistema deberá permitir iniciar un proceso de recuperación de contraseña.

#### RF-USR-007 — Gestión de sesiones

El sistema deberá permitir:

- Consultar sesiones activas.
- Revocar sesiones.
- Cerrar sesión actual.
- Cerrar todas las sesiones.

#### RF-USR-008 — Bloqueo temporal

El sistema deberá bloquear temporalmente una cuenta tras un número configurable de intentos fallidos.

### 5.4 Control horario avanzado

#### RF-TIM-001 — Inicio de jornada

El empleado deberá poder iniciar una jornada.

#### RF-TIM-002 — Inicio de pausa

El empleado deberá poder iniciar una pausa.

#### RF-TIM-003 — Fin de pausa

El empleado deberá poder finalizar una pausa.

#### RF-TIM-004 — Fin de jornada

El empleado deberá poder finalizar una jornada.

#### RF-TIM-005 — Corrección administrativa

El `TENANT_ADMIN` deberá poder corregir una jornada con motivo obligatorio.

#### RF-TIM-006 — Solicitud de corrección

El empleado deberá poder solicitar la corrección de una jornada.

#### RF-TIM-007 — Aprobación o rechazo

El `TENANT_ADMIN` deberá poder aprobar o rechazar solicitudes.

#### RF-TIM-008 — Historial de cambios

Toda modificación de jornada deberá conservar historial.

#### RF-TIM-009 — Jornadas incompletas

El sistema deberá detectar jornadas abiertas o incompletas.

#### RF-TIM-010 — Fichajes olvidados

El sistema deberá detectar posibles fichajes olvidados.

#### RF-TIM-011 — Horas extra

El sistema deberá calcular horas extra cuando existan reglas configuradas.

#### RF-TIM-012 — Redondeo

El tenant podrá configurar reglas de redondeo.

#### RF-TIM-013 — Límites de jornada

El tenant podrá configurar límites máximos de jornada.

#### RF-TIM-014 — Descansos obligatorios

El tenant podrá configurar descansos obligatorios.

### 5.5 Calendarios laborales

#### RF-CAL-001 — Crear calendario

El `TENANT_ADMIN` deberá poder crear calendarios laborales.

#### RF-CAL-002 — Definir días laborables

El calendario deberá permitir definir días laborables.

#### RF-CAL-003 — Gestionar festivos

El sistema deberá permitir registrar festivos.

#### RF-CAL-004 — Jornadas especiales

El sistema deberá permitir configurar jornadas especiales.

#### RF-CAL-005 — Vigencia temporal

Los calendarios deberán tener periodos de vigencia.

#### RF-CAL-006 — Asignación

Los calendarios podrán asignarse a:

- Todo el tenant.
- Equipos.
- Empleados.

#### RF-CAL-007 — Zona horaria

Los cálculos deberán respetar la zona horaria del tenant.

### 5.6 Ausencias

#### RF-ABS-001 — Tipos de ausencia

El sistema deberá soportar:

- Vacaciones.
- Permisos.
- Bajas.
- Ausencias justificadas.
- Ausencias no justificadas.

#### RF-ABS-002 — Solicitud de ausencia

El empleado deberá poder solicitar una ausencia.

#### RF-ABS-003 — Aprobación o rechazo

El `TENANT_ADMIN` o `TEAM_MANAGER` deberá poder aprobar o rechazar solicitudes.

#### RF-ABS-004 — Calendario de equipo

El sistema deberá mostrar las ausencias aprobadas en un calendario de equipo.

#### RF-ABS-005 — Historial

El sistema deberá conservar el historial de solicitudes y resoluciones.

#### RF-ABS-006 — Motivo obligatorio

Las resoluciones podrán requerir comentario o motivo.

### 5.7 Turnos básicos

#### RF-SHF-001 — Plantillas de turno

El `TENANT_ADMIN` deberá poder crear plantillas de turno.

#### RF-SHF-002 — Asignación de turno

El sistema deberá permitir asignar turnos a empleados.

#### RF-SHF-003 — Turnos nocturnos

El sistema deberá soportar turnos que crucen medianoche.

#### RF-SHF-004 — Vigencia

Las asignaciones deberán tener fecha de inicio y fin.

#### RF-SHF-005 — Comparación previsto-real

El sistema deberá comparar el tiempo planificado con el tiempo registrado.

#### RF-SHF-006 — Consulta de turnos

El empleado deberá poder consultar sus turnos.

### 5.8 Informes

#### RF-REP-001 — Informe por empleado

El sistema deberá generar informes por empleado.

#### RF-REP-002 — Informe por equipo

El sistema deberá generar informes por equipo.

#### RF-REP-003 — Informe por periodo

Los informes deberán poder filtrarse por periodo.

#### RF-REP-004 — Métricas

Los informes deberán incluir:

- Horas trabajadas.
- Pausas.
- Horas extra.
- Ausencias.
- Jornadas incompletas.
- Desviación respecto al horario previsto.

#### RF-REP-005 — Exportación CSV

El sistema deberá permitir exportar informes a CSV.

#### RF-REP-006 — Exportación PDF

La exportación a PDF será opcional.

#### RF-REP-007 — Restricción por tenant

Los informes deberán incluir únicamente datos del tenant autenticado.

### 5.9 Notificaciones

#### RF-NOT-001 — Notificaciones internas

El sistema deberá mostrar notificaciones internas.

#### RF-NOT-002 — Notificaciones por correo

El sistema deberá poder enviar correos.

#### RF-NOT-003 — Eventos notificables

El sistema podrá notificar:

- Jornada incompleta.
- Fichaje olvidado.
- Corrección aprobada.
- Corrección rechazada.
- Ausencia aprobada.
- Ausencia rechazada.

#### RF-NOT-004 — Procesamiento asíncrono

Las notificaciones deberán procesarse mediante Outbox y tareas programadas.

#### RF-NOT-005 — Reintentos

Los envíos fallidos deberán poder reintentarse.

#### RF-NOT-006 — Trazabilidad

El sistema deberá conservar estado e historial de envío.

### 5.10 Auditoría

#### RF-AUD-001 — Auditoría de plataforma

El sistema deberá auditar acciones de `PLATFORM_ADMIN`.

#### RF-AUD-002 — Auditoría de tenant

El sistema deberá auditar acciones administrativas dentro del tenant.

#### RF-AUD-003 — Contenido mínimo

Cada evento de auditoría deberá incluir:

- Actor.
- Acción.
- Entidad.
- Identificador de entidad.
- Tenant.
- Fecha.
- Correlation ID.
- Estado anterior.
- Estado posterior.
- Motivo, cuando corresponda.

#### RF-AUD-004 — Inmutabilidad

Los registros de auditoría no deberán poder modificarse mediante API.

### 5.11 Outbox

#### RF-OUT-001 — Persistencia transaccional

El evento de integración deberá persistirse en la misma transacción que el cambio de negocio.

#### RF-OUT-002 — Estados

Los mensajes Outbox deberán soportar:

- `PENDING`
- `PROCESSING`
- `PUBLISHED`
- `FAILED`

#### RF-OUT-003 — Polling

El sistema deberá procesar mensajes Outbox mediante una tarea programada.

#### RF-OUT-004 — Reintentos

Los mensajes fallidos deberán reintentarse.

#### RF-OUT-005 — Idempotencia

Los consumidores internos deberán ser idempotentes.

#### RF-OUT-006 — Trazabilidad

Cada mensaje deberá incluir:

- `eventId`
- `eventType`
- `eventVersion`
- `tenantId`
- `aggregateId`
- `occurredAt`
- `payload`

## 6. Requisitos no funcionales

- `RNF-001`: El sistema deberá implementarse como monolito modular.
- `RNF-002`: Las dependencias deberán apuntar hacia dominio y aplicación.
- `RNF-003`: Las reglas de negocio deberán residir en agregados, objetos de valor o servicios de dominio.
- `RNF-004`: El código deberá seguir SOLID, KISS, YAGNI y DRY.
- `RNF-005`: El sistema deberá aplicar seguridad por diseño.
- `RNF-006`: No deberá existir acceso cruzado entre tenants.
- `RNF-007`: PostgreSQL será la base de datos principal.
- `RNF-008`: Flyway gestionará los cambios de esquema.
- `RNF-009`: La API deberá documentarse con OpenAPI.
- `RNF-010`: Los errores deberán utilizar Problem Details.
- `RNF-011`: Los instantes deberán almacenarse en UTC.
- `RNF-012`: Cada tenant tendrá una zona horaria IANA.
- `RNF-013`: Las entidades críticas deberán utilizar bloqueo optimista.
- `RNF-014`: Objetivo de cobertura: dominio 90 % y aplicación 80 %.
- `RNF-015`: Las pruebas de integración usarán PostgreSQL mediante Testcontainers.
- `RNF-016`: ArchUnit verificará las reglas de dependencias.
- `RNF-017`: Las operaciones habituales deberán responder en tiempos adecuados para uso interactivo.
- `RNF-018`: Los listados grandes deberán estar paginados.
- `RNF-019`: Los logs deberán ser estructurados.
- `RNF-020`: Cada petición deberá tener correlation ID.
- `RNF-021`: No deberán existir secretos en el repositorio.
- `RNF-022`: La documentación se actualizará como parte de la Definition of Done.
- `RNF-023`: El sistema deberá ejecutarse mediante Docker Compose.
- `RNF-024`: El frontend deberá aplicar criterios básicos de accesibilidad.
- `RNF-025`: El frontend deberá ser responsive.

## 7. Requisitos de seguridad

- `RS-001`: Las contraseñas deberán almacenarse usando un algoritmo seguro.
- `RS-002`: Los access tokens deberán tener duración limitada.
- `RS-003`: Los refresh tokens deberán ser rotatorios.
- `RS-004`: El refresh token deberá almacenarse en cookie `HttpOnly`, `Secure` y `SameSite`.
- `RS-005`: El sistema deberá proteger los flujos basados en cookie frente a CSRF.
- `RS-006`: CORS deberá limitarse a orígenes configurados.
- `RS-007`: Los endpoints sensibles deberán aplicar rate limiting.
- `RS-008`: Las cuentas deberán bloquearse temporalmente tras intentos fallidos.
- `RS-009`: Todas las entradas deberán validarse en servidor.
- `RS-010`: Cada endpoint deberá validar los roles requeridos.
- `RS-011`: Cada operación deberá validar el tenant autenticado.
- `RS-012`: Las operaciones críticas de plataforma podrán exigir reautenticación.
- `RS-013`: Las acciones sensibles deberán quedar auditadas.
- `RS-014`: No deberán registrarse contraseñas, tokens, cookies ni datos innecesarios.
- `RS-015`: La CI deberá analizar dependencias vulnerables.
- `RS-016`: La CI deberá detectar secretos.

## 8. Requisitos de operación

- `RO-001`: El backend deberá exponer health checks.
- `RO-002`: Los logs deberán incluir timestamp, nivel, correlation ID, tenant ID, user ID y caso de uso cuando corresponda.
- `RO-003`: El sistema deberá registrar métricas de peticiones, errores, latencia, logins fallidos, Outbox y notificaciones.
- `RO-004`: El sistema podrá incluir un panel técnico básico.
- `RO-005`: PostgreSQL deberá contar con backups periódicos.
- `RO-006`: Deberá existir una política de retención de backups.
- `RO-007`: Deberá documentarse y probarse un procedimiento de restauración.
- `RO-008`: La configuración deberá gestionarse mediante variables de entorno y ficheros documentados.

## 9. Requisitos de testing

- `RT-001`: Cada regla de negocio nueva deberá contar con pruebas unitarias.
- `RT-002`: Los cambios en persistencia, seguridad y Outbox deberán contar con pruebas de integración.
- `RT-003`: Toda consulta o endpoint nuevo deberá comprobar aislamiento multitenant.
- `RT-004`: Cada rol deberá probarse frente a operaciones permitidas y prohibidas.
- `RT-005`: Deberán existir pruebas end-to-end de los flujos críticos.
- `RT-006`: Deberán probarse doble cierre de jornada, doble aprobación y doble procesamiento Outbox.
- `RT-007`: Las migraciones deberán probarse desde una base limpia.
- `RT-008`: La restauración de backup deberá validarse y documentarse.

## 10. Requisitos de documentación

- `RD-001`: El repositorio deberá incluir un README actualizado.
- `RD-002`: El repositorio deberá incluir `AGENTS.md`.
- `RD-003`: El repositorio deberá incluir skills para tareas repetibles.
- `RD-004`: Toda decisión arquitectónica relevante deberá registrarse mediante ADR.
- `RD-005`: La API deberá estar documentada con OpenAPI.
- `RD-006`: Los eventos de integración deberán estar documentados.
- `RD-007`: El sistema deberá incluir un modelo de amenazas.
- `RD-008`: La estrategia de pruebas deberá documentarse.
- `RD-009`: Deberán documentarse despliegue, backups, restauración, gestión de fallos y reintento de Outbox.

## 11. Restricciones

- `RC-001`: No introducir microservicios.
- `RC-002`: No introducir RabbitMQ.
- `RC-003`: No introducir Kafka.
- `RC-004`: No introducir MFA.
- `RC-005`: No introducir SSO.
- `RC-006`: No desarrollar API pública para terceros.
- `RC-007`: No desarrollar facturación SaaS.
- `RC-008`: No diseñar para alta disponibilidad.
- `RC-009`: No diseñar para escalado horizontal.
- `RC-010`: No introducir Kubernetes.
- `RC-011`: No implementar event sourcing.
- `RC-012`: No implementar CQRS completo.

## 12. Criterios de aceptación globales

La V2 se considerará completada cuando:

1. Exista un panel de administración de tenants.
2. El registro público esté protegido.
3. Los tenants soporten ciclo de vida.
4. Los tenants suspendidos no puedan operar.
5. Exista recuperación de contraseña.
6. Exista gestión de sesiones.
7. Existan reglas horarias.
8. Existan calendarios laborales.
9. Exista gestión de ausencias.
10. Existan turnos básicos.
11. Existan informes avanzados.
12. Existan notificaciones internas o por correo.
13. Las operaciones críticas estén auditadas.
14. El Outbox funcione de forma transaccional.
15. Los reintentos sean idempotentes.
16. Existan backups.
17. La restauración esté documentada y probada.
18. La cobertura cumpla los objetivos.
19. Las pruebas cross-tenant pasen.
20. La documentación esté actualizada.
21. El despliegue mediante Docker Compose sea reproducible.
22. No existan secretos en el repositorio.
23. La CI esté en verde.

## 13. Priorización

### Prioridad alta

- Consolidación del MVP.
- Administración de tenants.
- Seguridad.
- Recuperación de contraseña.
- Gestión de sesiones.
- Reglas horarias.
- Calendarios.
- Ausencias.
- Informes.
- Auditoría.
- Backups.
- Restauración.

### Prioridad media

- Turnos.
- Notificaciones.
- Panel técnico.
- Exportación PDF.
- Roles adicionales.
- Métricas avanzadas.

### Prioridad baja

- Anti-bot avanzado.
- Row-Level Security.
- Personalización visual.
- Mutation testing.
- Regresión visual.
- Auditoría avanzada.

## 14. Trazabilidad inicial

| Objetivo | Requisitos relacionados |
|---|---|
| Administración segura de tenants | RF-TEN-001 a RF-TEN-010 |
| Registro público controlado | RF-REG-001 a RF-REG-006 |
| Seguridad de usuarios | RF-USR-006 a RF-USR-008, RS-001 a RS-016 |
| Control horario avanzado | RF-TIM-001 a RF-TIM-014 |
| Calendarios | RF-CAL-001 a RF-CAL-007 |
| Ausencias | RF-ABS-001 a RF-ABS-006 |
| Turnos | RF-SHF-001 a RF-SHF-006 |
| Informes | RF-REP-001 a RF-REP-007 |
| Notificaciones | RF-NOT-001 a RF-NOT-006 |
| Auditoría | RF-AUD-001 a RF-AUD-004 |
| Outbox | RF-OUT-001 a RF-OUT-006 |
| Operación | RO-001 a RO-008 |
| Calidad | RNF-001 a RNF-025, RT-001 a RT-008 |

## 15. Resultado esperado

La V2 deberá resultar en una aplicación:

- Segura.
- Multitenant.
- Administrable.
- Auditable.
- Funcionalmente más completa.
- Desplegable.
- Recuperable ante fallos.
- Testeada.
- Documentada.
- Mantenible.
- Sin complejidad distribuida innecesaria.
