
# Ajuste de alcance de la V2

## Funcionalidades descartadas

La V2 no incluirá:

* MFA.
* SSO.
* API pública para terceros.
* RabbitMQ.
* Kafka.
* Facturación SaaS.
* Alta disponibilidad.
* Escalabilidad horizontal.
* Microservicios.
* Infraestructura distribuida.

Estas capacidades quedan fuera del alcance del proyecto y no deberán condicionar la arquitectura ni generar abstracciones anticipadas.

## Arquitectura resultante

La arquitectura se mantendrá como:

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

* Clean Architecture.
* DDD táctico.
* Seguridad basada en autenticación y autorización.
* Multitenancy con esquema compartido.
* Eventos de dominio locales.
* Transactional Outbox.
* Publicador interno mediante polling.
* Auditoría.
* Testing automatizado.
* Documentación viva.

El patrón Outbox se conservará por su valor transaccional y académico, pero no publicará eventos en brokers externos.

El adaptador de publicación podrá:

* Registrar el evento como procesado.
* Ejecutar consumidores internos.
* Generar notificaciones internas o correos.
* Mantener la posibilidad conceptual de integración futura.

No será necesario implementar una infraestructura genérica de mensajería.

## Prioridades revisadas de la V2

### 1. Administración segura de tenants

La V2 deberá incluir:

* Registro controlado de tenants.
* Verificación de correo, si se considera necesaria.
* Estados del ciclo de vida del tenant.
* Panel de administración de plataforma.
* Rol `PLATFORM_ADMIN`.
* Activación.
* Suspensión.
* Reactivación.
* Archivado.
* Auditoría de acciones globales.
* Posibilidad de deshabilitar el registro público.

Estados recomendados:

```text
PENDING
ACTIVE
SUSPENDED
ARCHIVED
```

No es necesario introducir estados relacionados con suscripciones, pagos o planes comerciales.

### 2. Gestión de usuarios y permisos

Mantener:

* `PLATFORM_ADMIN`.
* `TENANT_ADMIN`.
* `EMPLOYEE`.

Opcionalmente:

* `TEAM_MANAGER`.
* `AUDITOR`.

No es necesario implementar un motor complejo de permisos.

Se recomienda mantener RBAC y añadir permisos concretos únicamente cuando exista una necesidad funcional real.

### 3. Seguridad reforzada sin MFA ni SSO

Incluir:

* Contraseñas seguras.
* Recuperación de contraseña.
* Verificación de correo opcional.
* Tokens de acceso de corta duración.
* Refresh tokens rotatorios.
* Bloqueo temporal por intentos fallidos.
* Rate limiting.
* Gestión de sesiones.
* Cierre de sesión.
* Revocación de sesiones.
* Protección CSRF.
* CORS restringido.
* Validación de entradas.
* Auditoría.
* Escaneo de dependencias.
* Secret scanning.

Las cuentas de administración de plataforma se protegerán mediante:

* Contraseñas reforzadas.
* Sesiones más cortas.
* Reautenticación en operaciones sensibles.
* Auditoría completa.
* Prohibición de creación pública.

### 4. Gestión avanzada del control horario

Añadir:

* Corrección administrativa de jornadas.
* Reglas horarias por tenant.
* Descansos obligatorios.
* Límites de jornada.
* Jornadas incompletas.
* Fichajes olvidados.
* Alertas internas.
* Horas extra.
* Redondeo configurable.
* Motivos de modificación.
* Historial completo de cambios.

### 5. Calendarios laborales

Añadir:

* Días laborables.
* Festivos.
* Jornadas especiales.
* Horarios por tenant.
* Horarios por grupo o equipo.
* Cambios de horario por periodos.
* Turnos que crucen medianoche, si se incluyen turnos.

### 6. Ausencias

Añadir:

* Vacaciones.
* Permisos.
* Bajas.
* Ausencias justificadas.
* Solicitud y aprobación.
* Calendario de equipo.
* Historial de solicitudes.

La gestión de ausencias debe mantenerse separada del agregado de jornada.

### 7. Turnos

Puede incluirse con alcance limitado:

* Plantillas de turnos.
* Asignación de turnos.
* Turnos nocturnos.
* Comparación entre jornada prevista y real.

Se excluyen inicialmente:

* Optimización automática de turnos.
* Planificación compleja.
* Intercambio automático entre empleados.
* Algoritmos avanzados de asignación.

### 8. Informes

Añadir:

* Informes por empleado.
* Informes por equipo.
* Informes por periodo.
* Horas trabajadas.
* Pausas.
* Horas extra.
* Ausencias.
* Jornadas incompletas.
* Exportación CSV.
* Exportación PDF si el tiempo lo permite.

Los informes se generarán dentro del monolito.

No se creará un servicio independiente de reporting.

### 9. Notificaciones

Se podrán implementar:

* Notificaciones internas.
* Correos electrónicos.
* Recordatorios de fichaje.
* Correcciones aprobadas o rechazadas.
* Ausencias aprobadas.
* Jornadas incompletas.

Las notificaciones se ejecutarán mediante:

```text
Evento local
→ mensaje Outbox
→ tarea programada
→ envío
```

No se utilizará un broker.

### 10. Observabilidad simplificada

Incluir:

* Logs estructurados.
* Correlation ID.
* Métricas de aplicación.
* Health checks.
* Métricas de Outbox.
* Registro de tareas fallidas.
* Panel técnico básico.
* Alertas sencillas, si el entorno lo permite.

No se requiere:

* Trazado distribuido.
* Service mesh.
* Observabilidad entre microservicios.
* Infraestructura compleja de monitorización.

### 11. Despliegue

El despliegue podrá basarse en:

* Una instancia de backend.
* Una instancia de frontend.
* Una base de datos PostgreSQL.
* Docker Compose.
* Reverse proxy.
* HTTPS.
* Copias de seguridad.

No se requiere:

* Kubernetes.
* Balanceadores.
* Varias réplicas.
* Autoescalado.
* Multi-región.
* Réplicas de lectura.

### 12. Backups y recuperación

Aunque no se implemente alta disponibilidad, sí deberá existir:

* Copia de seguridad periódica.
* Política de retención.
* Procedimiento de restauración.
* Prueba documentada de recuperación.
* Protección de los backups.
* Copias de configuración necesarias.

La ausencia de alta disponibilidad no elimina la necesidad de recuperación ante fallos.

## Fases revisadas de la V2

### Fase 1. Consolidación del MVP

* Revisar arquitectura.
* Reducir deuda técnica.
* Mejorar cobertura.
* Actualizar documentación.
* Revisar seguridad.
* Corregir defectos funcionales.

### Fase 2. Administración de tenants

* Crear rol `PLATFORM_ADMIN`.
* Crear panel de plataforma.
* Gestionar activación, suspensión y archivado.
* Proteger el alta pública.
* Añadir auditoría de plataforma.

### Fase 3. Seguridad

* Recuperación de contraseña.
* Verificación de correo opcional.
* Gestión de sesiones.
* Rate limiting.
* Bloqueo de cuentas.
* Escaneo de dependencias y secretos.

### Fase 4. Reglas horarias y calendarios

* Horarios.
* Festivos.
* Jornadas esperadas.
* Descansos.
* Detección de anomalías.

### Fase 5. Ausencias

* Vacaciones.
* Permisos.
* Bajas.
* Aprobaciones.
* Calendario de equipo.

### Fase 6. Turnos básicos

* Plantillas.
* Asignaciones.
* Turnos nocturnos.
* Comparación entre planificación y fichaje.

### Fase 7. Informes

* Resúmenes.
* Filtros.
* Exportación CSV.
* Exportación PDF opcional.

### Fase 8. Notificaciones

* Correos.
* Notificaciones internas.
* Recordatorios.
* Procesamiento mediante Outbox.

### Fase 9. Operación y calidad

* Logs.
* Métricas.
* Health checks.
* Backups.
* Restauración.
* Pruebas end-to-end.
* Revisión de seguridad.
* Documentación operativa.

## ADR que dejan de ser necesarios

No será necesario crear ADR sobre:

* Selección de RabbitMQ frente a Kafka.
* Estrategia de SSO.
* MFA.
* API pública.
* Facturación.
* Kubernetes.
* Autoescalado.
* Alta disponibilidad.
* Multi-región.
* Extracción de microservicios.

## ADR que siguen siendo relevantes

Mantener o crear ADR para:

* Administración de plataforma.
* Registro controlado de tenants.
* Ciclo de vida del tenant.
* Recuperación de contraseña.
* Gestión de sesiones.
* Calendarios laborales.
* Ausencias.
* Turnos.
* Notificaciones mediante Outbox.
* Estrategia de backups.
* Generación de informes.
* Retención y eliminación de datos.

## Resultado esperado

La V2 deberá convertirse en una aplicación:

* Segura.
* Funcionalmente completa.
* Administrable.
* Auditable.
* Testeada.
* Documentada.
* Fácil de desplegar.
* Fácil de mantener.
* Preparada para usuarios reales.
* Sin complejidad distribuida innecesaria.

La solución objetivo queda resumida así:

```text
Monolito modular
+ administración de tenants
+ control horario avanzado
+ calendarios
+ ausencias
+ turnos básicos
+ informes
+ notificaciones internas
+ Outbox
+ seguridad
+ auditoría
+ backups
+ observabilidad básica
```
