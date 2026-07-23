# Planificación V2 — Evolución del MVP a producto SaaS serio

## 1. Objetivo de la V2

La V2 debe transformar el MVP en una plataforma SaaS más madura, centrada en:

* Fiabilidad operativa.
* Seguridad reforzada.
* Mejor experiencia de usuario.
* Escalabilidad técnica.
* Observabilidad.
* Automatización.
* Soporte a más casos de negocio.
* Preparación para clientes reales.
* Reducción de deuda técnica.
* Procesos de despliegue y soporte más profesionales.

La V2 no debería convertirse todavía en una plataforma excesivamente compleja ni en una arquitectura de microservicios generalizada.

La recomendación es evolucionar desde:

```text
MVP funcional
```

hacia:

```text
Producto SaaS modular, observable, seguro y operable
```

---

# 2. Principios de evolución

La V2 deberá respetar:

* Monolito modular como opción por defecto.
* Extracción de servicios solo cuando exista una necesidad real.
* Evolución incremental.
* Compatibilidad hacia atrás.
* Migraciones seguras.
* Automatización de pruebas.
* Seguridad por defecto.
* Observabilidad desde el diseño.
* Métricas de producto y operación.
* Gestión explícita de deuda técnica.
* Uso de feature flags.
* Desarrollo basado en ADR.
* Eventos de integración versionados.
* Procesos idempotentes.

---

# 3. Objetivos de producto

## 3.1 Mejorar el control horario

Añadir:

* Edición controlada de jornadas.
* Reglas horarias por empresa.
* Configuración de descansos obligatorios.
* Límites de jornada.
* Detección automática de anomalías.
* Fichajes olvidados.
* Jornadas incompletas.
* Alertas por exceso de horas.
* Redondeo configurable.
* Registro manual con autorización.
* Motivos de modificación obligatorios.

## 3.2 Gestión de calendario laboral

Añadir:

* Calendarios laborales por tenant.
* Festivos.
* Días laborables.
* Jornadas especiales.
* Horarios de verano.
* Diferentes calendarios por centro o grupo.

## 3.3 Gestión de turnos

Añadir:

* Plantillas de turnos.
* Asignación de turnos.
* Rotaciones.
* Turnos nocturnos.
* Turnos que cruzan medianoche.
* Cambios de turno.
* Comparación entre planificación y tiempo real.

## 3.4 Gestión de ausencias

Añadir:

* Vacaciones.
* Bajas.
* Permisos.
* Ausencias justificadas.
* Ausencias no justificadas.
* Solicitud y aprobación.
* Saldos.
* Calendario de equipo.

## 3.5 Informes avanzados

Añadir:

* Informes por empleado.
* Informes por equipo.
* Informes por centro.
* Desviaciones respecto al horario esperado.
* Horas extra.
* Ausencias.
* Exportaciones programadas.
* CSV y PDF.
* Filtros avanzados.
* Dashboards.
* Indicadores de cumplimiento.

---

# 4. Nuevos roles y permisos

La V2 debería evolucionar desde roles simples hacia permisos más granulares.

Roles sugeridos:

* `PLATFORM_ADMIN`
* `TENANT_OWNER`
* `TENANT_ADMIN`
* `HR_MANAGER`
* `TEAM_MANAGER`
* `AUDITOR`
* `EMPLOYEE`

Permisos posibles:

* Gestionar usuarios.
* Gestionar roles.
* Gestionar horarios.
* Consultar jornadas.
* Modificar jornadas.
* Aprobar correcciones.
* Gestionar ausencias.
* Consultar informes.
* Exportar datos.
* Consultar auditoría.
* Gestionar integraciones.
* Gestionar facturación.

La autorización debería pasar de:

```text
role-based access control
```

a:

```text
role-based access control
+ permission-based access control
+ scope por tenant y equipo
```

---

# 5. Mejoras del modelo multitenant

## 5.1 Aislamiento reforzado

Mantener inicialmente base de datos y esquema compartidos, pero reforzar:

* Filtros globales por tenant.
* Repositorios tenant-aware.
* Restricciones compuestas.
* Auditorías automáticas.
* Pruebas sistemáticas cross-tenant.
* PostgreSQL Row-Level Security como posible capa adicional.

## 5.2 Preparación para tenants grandes

Añadir:

* Límites configurables.
* Cuotas.
* Paginación obligatoria.
* Rate limiting por tenant.
* Métricas por tenant.
* Feature flags por tenant.
* Configuración por plan.

## 5.3 Estrategia híbrida futura

Preparar la posibilidad de:

* Tenants pequeños en esquema compartido.
* Tenants grandes en esquema o base dedicada.
* Migración progresiva entre estrategias.

No implementar esta complejidad salvo que exista una necesidad demostrable.

---

# 6. Seguridad V2

## 6.1 Identidad

Añadir:

* Recuperación de contraseña.
* Verificación de correo.
* MFA.
* Gestión de sesiones.
* Cierre de sesiones remotas.
* Políticas de contraseña.
* Bloqueo por intentos fallidos.
* Detección de actividad sospechosa.

## 6.2 SSO

Incorporar opcionalmente:

* OpenID Connect.
* SAML para clientes empresariales.
* Integración con proveedores corporativos.
* Aprovisionamiento SCIM como evolución futura.

## 6.3 Protección de datos

Añadir:

* Cifrado de campos sensibles.
* Rotación de secretos.
* Gestión centralizada de secretos.
* Retención configurable.
* Anonimización.
* Exportación de datos personales.
* Eliminación controlada.
* Política de backups.
* Pruebas de restauración.

## 6.4 Seguridad de aplicación

Añadir:

* SAST.
* DAST.
* Dependency scanning.
* Secret scanning.
* Container scanning.
* SBOM.
* Revisión periódica de permisos.
* Threat modeling actualizado.
* Pruebas de penetración.

---

# 7. Arquitectura V2

## 7.1 Mantener monolito modular

Recomendación:

```text
Monolito modular
+ módulos bien delimitados
+ eventos de dominio
+ outbox
+ integración asíncrona
```

Módulos V2:

* Identity and Access.
* Tenant Management.
* Time Tracking.
* Scheduling.
* Absence Management.
* Corrections.
* Reporting.
* Audit.
* Notifications.
* Billing.
* Integrations.

## 7.2 Modularidad reforzada

Añadir:

* APIs internas explícitas.
* Prohibición de acceso directo entre repositorios de módulos.
* Contratos de módulo.
* Pruebas ArchUnit.
* Módulos de Spring si aporta valor.
* Publicación de eventos internos.
* Catálogo de dependencias.

## 7.3 Extracción selectiva

Los primeros candidatos a extraer si fuera necesario serían:

* Notificaciones.
* Reporting pesado.
* Integraciones externas.
* Procesamiento de documentos.
* Facturación.

No extraer:

* Núcleo de control horario.
* Correcciones.
* Reglas transaccionales críticas.

salvo que existan límites claros y necesidades operativas reales.

---

# 8. Eventos y mensajería

## 8.1 Introducción de broker

En V2 sí puede justificarse RabbitMQ o Kafka.

### RabbitMQ

Adecuado para:

* Notificaciones.
* Tareas.
* Work queues.
* Reintentos.
* Integraciones.

### Kafka

Adecuado si se necesita:

* Alto volumen.
* Replay.
* Stream processing.
* Analítica.
* Múltiples consumidores.
* Retención de eventos.

Para esta aplicación, RabbitMQ suele ser suficiente como primera evolución.

## 8.2 Evolución del Outbox

Añadir:

* Publicación real a broker.
* Reintentos exponenciales.
* Dead-letter queue.
* Métricas.
* Alertas.
* Limpieza automática.
* Archivado.
* Reprocesamiento manual.
* Idempotencia de consumidores.
* Versionado de contratos.

## 8.3 Catálogo de eventos

Mantener un catálogo con:

* Nombre.
* Versión.
* Productor.
* Consumidores.
* Esquema.
* Política de compatibilidad.
* Ejemplos.
* Información sensible.
* Estrategia de deprecación.

---

# 9. Notificaciones

Añadir un módulo de notificaciones para:

* Fichaje olvidado.
* Jornada incompleta.
* Corrección aprobada.
* Corrección rechazada.
* Solicitud pendiente.
* Ausencia aprobada.
* Exceso de horas.
* Recordatorios.

Canales:

* Email.
* Notificaciones internas.
* Webhooks.
* Push como evolución posterior.

Requisitos:

* Plantillas.
* Idiomas.
* Preferencias.
* Reintentos.
* Idempotencia.
* Trazabilidad.
* No bloquear transacciones de negocio.

---

# 10. Integraciones

## 10.1 Webhooks

Permitir que clientes reciban eventos como:

* Empleado creado.
* Jornada cerrada.
* Corrección aprobada.
* Ausencia aprobada.

Requisitos:

* Firma HMAC.
* Reintentos.
* Historial de entregas.
* Desactivación automática por errores.
* Idempotency key.
* Versionado.
* Rate limiting.

## 10.2 Exportaciones

Añadir:

* Exportación a sistemas de nómina.
* Exportación periódica.
* Formatos configurables.
* Plantillas por proveedor.
* SFTP, API o webhook.

## 10.3 API pública

Preparar:

* API keys.
* OAuth 2.0 client credentials.
* Límites por cliente.
* Scopes.
* Portal de documentación.
* Versionado.
* Auditoría.

---

# 11. Facturación SaaS

La V2 puede introducir un módulo básico de billing.

Funcionalidades:

* Planes.
* Suscripciones.
* Número máximo de empleados.
* Funcionalidades por plan.
* Periodos de prueba.
* Estado de suscripción.
* Facturas.
* Renovaciones.
* Cancelación.
* Impagos.

Recomendación:

* Mantener billing desacoplado del dominio de control horario.
* Utilizar eventos.
* No bloquear operaciones críticas por fallos temporales del proveedor de pagos.
* Introducir feature flags por plan.

---

# 12. Observabilidad

## 12.1 Logs

Añadir:

* Logs estructurados.
* Correlation ID.
* Trace ID.
* Tenant ID.
* User ID.
* Event ID.
* Job ID.
* Nivel configurable.

## 12.2 Métricas

Métricas mínimas:

* Latencia.
* Tasa de error.
* Peticiones por endpoint.
* Logins fallidos.
* Jornadas abiertas.
* Mensajes Outbox pendientes.
* Reintentos.
* DLQ.
* Tareas programadas.
* Consumo por tenant.
* Uso de base de datos.

## 12.3 Trazas

Añadir OpenTelemetry para:

* Peticiones HTTP.
* Acceso a base de datos.
* Publicación de eventos.
* Consumo de eventos.
* Integraciones externas.

## 12.4 Alertas

Alertar sobre:

* Errores elevados.
* Outbox atascado.
* DLQ creciente.
* Alta latencia.
* Fallos de autenticación.
* Backups fallidos.
* Jobs fallidos.
* Consumo anómalo.

---

# 13. Alta disponibilidad y escalabilidad

## 13.1 Aplicación

Preparar:

* Instancias stateless.
* Balanceador.
* Health checks.
* Readiness y liveness.
* Despliegue rolling.
* Escalado horizontal.
* Rate limiting distribuido.

## 13.2 Base de datos

Añadir:

* Pool de conexiones.
* Índices revisados.
* Monitorización de queries.
* Réplicas de lectura si son necesarias.
* Backup automático.
* Point-in-time recovery.
* Pruebas de restauración.

## 13.3 Caché

Introducir Redis solo para casos concretos:

* Rate limiting.
* Sesiones.
* Locks distribuidos.
* Configuración.
* Caché de consultas costosas.

No utilizar Redis como solución general sin medir.

---

# 14. Frontend V2

Mejoras:

* Diseño responsive.
* Accesibilidad.
* Internacionalización.
* Tema configurable.
* Dashboards.
* Tablas avanzadas.
* Exportaciones.
* Filtros persistentes.
* Estados vacíos.
* Notificaciones.
* Gestión de errores.
* Optimistic UI en operaciones seguras.
* Lazy loading.
* Control granular de permisos.

Añadir pruebas:

* Unitarias.
* Componentes.
* Integración.
* End-to-end.
* Accesibilidad.
* Regresión visual opcional.

---

# 15. Calidad y testing V2

## 15.1 Testing

Añadir:

* Contract testing.
* Mutation testing.
* Performance testing.
* Load testing.
* Security testing.
* Resilience testing.
* Tests de migración.
* Tests de compatibilidad de eventos.

## 15.2 Objetivos

Orientativamente:

* Dominio: 90 %.
* Aplicación: 85 %.
* Casos críticos: cobertura funcional completa.
* Mutation score en dominio crítico.
* E2E para flujos clave.

## 15.3 Rendimiento

Escenarios:

* Cierre de jornada.
* Consulta de históricos.
* Informes.
* Importación masiva.
* Publicación de eventos.
* Concurrencia de fichajes.

---

# 16. DevOps y CI/CD

Añadir:

* Pipelines por pull request.
* Quality gates.
* Análisis de seguridad.
* Creación de imágenes.
* Firma de artefactos.
* Despliegue automático a staging.
* Despliegue manual o aprobado a producción.
* Migraciones controladas.
* Rollback.
* Feature flags.
* Canary release opcional.

Entornos:

* Local.
* Test.
* Staging.
* Producción.

---

# 17. Gestión de configuración

Añadir:

* Configuración por entorno.
* Configuración por tenant.
* Feature flags.
* Flags por plan.
* Configuración versionada.
* Auditoría de cambios.
* Validación de configuración.

Evitar:

* Configuraciones ocultas en código.
* Dependencia de variables no documentadas.
* Cambios manuales sin trazabilidad.

---

# 18. Datos y migraciones

La V2 deberá incluir:

* Migraciones compatibles.
* Estrategia expand-contract.
* Migraciones reversibles cuando sea posible.
* Backfills controlados.
* Jobs idempotentes.
* Validación de datos.
* Auditoría.
* Migración de contratos de eventos.

Regla:

```text
Primero añadir
Después migrar
Finalmente eliminar
```

---

# 19. Cumplimiento y privacidad

Añadir:

* Políticas de retención.
* Consentimientos cuando correspondan.
* Exportación de datos.
* Derecho de eliminación.
* Registro de accesos.
* Auditoría.
* Clasificación de datos.
* Procedimiento de incidentes.
* Acuerdos de tratamiento.
* Revisión normativa.

En el TFG puede documentarse la preparación sin implementar toda la parte jurídica.

---

# 20. Soporte operativo

Añadir:

* Panel técnico.
* Consulta de jobs.
* Reintento de mensajes.
* Estado de integraciones.
* Auditoría.
* Gestión de incidencias.
* Herramientas de diagnóstico.
* Runbooks.
* Procedimientos de recuperación.

Operaciones administrativas sensibles deberán:

* Estar autorizadas.
* Estar auditadas.
* Requerir motivo.
* Ser idempotentes cuando sea posible.

---

# 21. Fases de desarrollo V2

## Fase 1. Consolidación del MVP

Objetivos:

* Reducir deuda técnica.
* Revisar arquitectura.
* Mejorar cobertura.
* Resolver problemas de seguridad.
* Medir rendimiento.

Entregables:

* Auditoría técnica.
* Backlog de deuda.
* ADR actualizados.
* Métricas base.
* Refactorizaciones críticas.

## Fase 2. Seguridad reforzada

Entregables:

* MFA.
* Recuperación de contraseña.
* Gestión de sesiones.
* Rate limiting.
* SAST.
* Dependency scanning.
* Secret scanning.
* Threat model actualizado.

## Fase 3. Horarios y calendarios

Entregables:

* Calendarios laborales.
* Festivos.
* Horarios.
* Reglas de jornada.
* Detección de anomalías.

## Fase 4. Ausencias

Entregables:

* Vacaciones.
* Permisos.
* Bajas.
* Flujos de aprobación.
* Calendario de equipo.

## Fase 5. Turnos

Entregables:

* Plantillas.
* Asignaciones.
* Rotaciones.
* Turnos nocturnos.
* Comparación planificado-real.

## Fase 6. Informes avanzados

Entregables:

* Dashboard.
* Informes.
* Exportaciones.
* Jobs asíncronos.
* Descargas.

## Fase 7. Mensajería

Entregables:

* RabbitMQ o Kafka.
* Publicador Outbox.
* Consumidores.
* DLQ.
* Reintentos.
* Métricas.
* Idempotencia.

## Fase 8. Notificaciones

Entregables:

* Email.
* Plantillas.
* Preferencias.
* Eventos.
* Historial.

## Fase 9. Integraciones

Entregables:

* Webhooks.
* API keys.
* API pública.
* Exportaciones externas.

## Fase 10. Billing

Entregables:

* Planes.
* Suscripciones.
* Límites.
* Feature flags.
* Integración de pagos.

## Fase 11. Observabilidad y operación

Entregables:

* Métricas.
* Logs.
* Tracing.
* Alertas.
* Dashboards.
* Runbooks.

## Fase 12. Escalabilidad

Entregables:

* Despliegue horizontal.
* Redis si está justificado.
* Optimización de PostgreSQL.
* Pruebas de carga.
* Plan de capacidad.

---

# 22. Priorización

## Prioridad alta

* Consolidación del MVP.
* Seguridad.
* Observabilidad.
* Calendarios.
* Reglas horarias.
* Ausencias.
* Informes.
* Backups.
* Recuperación.
* CI/CD.

## Prioridad media

* Turnos.
* Notificaciones.
* Webhooks.
* Mensajería.
* API pública.
* Feature flags.
* MFA.

## Prioridad baja o condicionada

* Kafka.
* Microservicios.
* Multi-región.
* Base de datos por tenant.
* Event sourcing.
* CQRS completo.
* SCIM.
* Personalización avanzada.

---

# 23. ADR recomendados para V2

Crear ADR para:

* Introducción de broker.
* RabbitMQ frente a Kafka.
* Row-Level Security.
* Redis.
* Estrategia de SSO.
* Estrategia de permisos.
* Feature flags.
* Billing.
* Estrategia de backups.
* Estrategia de observabilidad.
* Estrategia de despliegue.
* Estrategia de migración.
* Estrategia de reporting.
* Extracción de servicios.
* API pública.
* Webhooks.
* Retención de datos.
* Alta disponibilidad.

---

# 24. Criterios de éxito de la V2

La V2 se considerará madura cuando:

1. Existan métricas y alertas operativas.
2. Las restauraciones de backup estén probadas.
3. La seguridad esté automatizada en CI.
4. Los tenants estén aislados mediante múltiples controles.
5. El sistema soporte múltiples instancias.
6. La mensajería tenga reintentos y DLQ.
7. Los consumidores sean idempotentes.
8. Los informes no bloqueen operaciones principales.
9. Las operaciones críticas estén auditadas.
10. Exista gestión de ausencias.
11. Exista configuración horaria.
12. Existan permisos granulares.
13. El frontend sea accesible y responsive.
14. El despliegue sea repetible.
15. Exista documentación operativa.
16. Las migraciones sean seguras.
17. Las integraciones estén versionadas.
18. El sistema soporte uso real de clientes piloto.

---

# 25. Recomendación estratégica

La mejor evolución no es pasar directamente a microservicios.

La secuencia recomendada es:

```text
MVP
↓
Monolito modular consolidado
↓
Observabilidad y seguridad
↓
Procesos asíncronos
↓
Broker de mensajería
↓
Integraciones
↓
Escalado horizontal
↓
Extracción selectiva de servicios
```

La V2 debería centrarse en convertir el sistema en un producto confiable y operable antes de introducir distribución arquitectónica adicional.

