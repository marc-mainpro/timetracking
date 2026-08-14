# Informe de cierre de la V2 (T160-05)

Fecha: 2026-08-07. Contrasta uno a uno los 23 criterios de aceptación globales
de `../specs/requisitos-v2-control-horario.md` §12 con evidencia verificable.

Criterio de evidencia: se cita el artefacto que lo demuestra —una prueba, una
migración, un documento— no la intención de haberlo hecho. Lo que no está
completo se marca como tal.

## Criterios de aceptación

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Panel de administración de tenants | ✅ | `frontend/src/app/features/platform/`, `PlatformTenantController`, `PlatformTenantControllerIntegrationTest` |
| 2 | Registro público protegido | ✅ | Alta en tres pasos con verificación y aprobación (ADR-0016), flag apagado por defecto, rate limiting por patrón, respuesta anti-enumeración. `registro-publico.spec.ts`, `PublicRegistrationDisabledIntegrationTest` |
| 3 | Ciclo de vida del tenant | ✅ | `Tenant` con PENDING/ACTIVE/SUSPENDED/ARCHIVED y transiciones en el agregado (ADR-0010), migración V10, `TenantTest` |
| 4 | Tenant suspendido no opera | ✅ | `UserStatusFilter` + `TenantAccessRepository` en login, refresh y toda petición autenticada. `aislamiento.spec.ts` («un tenant suspendido deja de operar y vuelve al reactivarlo») |
| 5 | Recuperación de contraseña | ✅ | Token de un solo uso almacenado como hash, caducidad, revocación de sesiones, respuesta anti-enumeración. `PasswordResetControllerIntegrationTest` |
| 6 | Gestión de sesiones | ✅ | Agregado `Session`, `GET/DELETE /api/v1/auth/sessions`, revocación individual y masiva. `SessionControllerIntegrationTest` |
| 7 | Reglas horarias | ✅ | Redondeo, tolerancias, jornada máxima y descanso obligatorio; motor de evaluación con pruebas de cambio de hora. `WorkdayEvaluationEngine`, `AdminHourlyRulesControllerIntegrationTest` |
| 8 | Calendarios laborales | ✅ | Festivos, jornadas especiales, vigencia y resolución por ámbito más específico (ADR-0017). `calendario-turno.spec.ts` |
| 9 | Gestión de ausencias | ✅ | Solicitud, aprobación, rechazo y efecto sobre el tiempo esperado. `ausencia-notificacion.spec.ts` |
| 10 | Turnos básicos | ✅ | Plantillas, asignaciones, cruce de medianoche y previsto-real (T90-06). `calendario-turno.spec.ts` |
| 11 | Informes avanzados | ✅ | Por empleado, equipo y periodo, con horas, pausas, extras, desviación, y exportación CSV y PDF. `TimeSummaryPdfWriterTest` |
| 12 | Notificaciones | ✅ | Internas con contador de no leídas y envío por correo con reintentos, vía Outbox. `NotificationControllerIntegrationTest`, `ausencia-notificacion.spec.ts` |
| 13 | Operaciones críticas auditadas | ✅ | Auditoría append-only de plataforma y de tenant, sin endpoints de modificación. `AuditEventControllerIntegrationTest` |
| 14 | Outbox transaccional | ✅ | Mensaje y cambio de negocio en la misma transacción, con pruebas de rollback conjunto. `OutboxGuaranteesIntegrationTest`, `*AtomicityIntegrationTest` |
| 15 | Reintentos idempotentes | ✅ | Deduplicación por `(eventId, consumidor)` con reserva atómica; el fallo de un consumidor reintenta en vez de darse por publicado. `JdbcProcessedEventStoreIntegrationTest`, `LoggingIntegrationEventPublisherTest` |
| 16 | Backups | ✅ | `scripts/backup/backup-postgres.sh` con cifrado, rotación GFS y códigos de salida (ADR-0013) |
| 17 | Restauración documentada y probada | ✅ | Simulacro ejecutado el 2026-08-04: recuperación en 13 s y smoke tests en verde. Acta en `docs/manuals/backup-restore.md` §4 |
| 18 | Cobertura | ✅ | Umbrales 90 % dominio / 80 % aplicación aplicados en `verify`; el build falla si bajan |
| 19 | Pruebas cross-tenant | ✅ | `CrossTenantSecurityIntegrationTest`, pruebas por módulo y `aislamiento.spec.ts`. Recurso ajeno responde 404, no 403 |
| 20 | Documentación actualizada | ✅ | Este cierre (T160-04): README, `docs/architecture/components.md`, OpenAPI reexportado, catálogo de eventos, matriz de trazabilidad, estrategia de pruebas, revisión OWASP y de rendimiento |
| 21 | Docker Compose reproducible | ✅ | `docker compose up -d --build` levanta postgres, backend, frontend y mailpit; `scripts/smoke.sh` valida el arranque |
| 22 | Sin secretos en el repositorio | ✅ | gitleaks en CI sobre todo el historial, con allowlist por literal y no por ruta, verificada con una clave inyectada a propósito |
| 23 | CI en verde | ⚠️ Parcial | Backend, frontend, Docker, E2E, secret scanning y `npm audit` en verde. **El análisis de dependencias del backend no se ejecuta** hasta dar de alta `NVD_API_KEY` |

**22 de 23 completos; 1 parcial con causa declarada (`NVD_API_KEY`); ninguno incumplido.**

## Riesgos pendientes y deuda aceptada

| Asunto | Impacto | Decisión | Revisión |
|---|---|---|---|
| `NVD_API_KEY` sin dar de alta | RS-015 solo cubre frontend; las dependencias Java no se analizan | Requiere acceso a la configuración del repositorio en GitHub y una cuenta en el NIST: no puede resolverse desde el código | Al dar de alta el secreto |
| 24 advisories *high/critical* de `npm audit` | 5 de runtime (cadena Angular 19, corregibles solo con Angular 21) y 19 de la cadena de build, que no se empaqueta | Excepción aprobada por advisory en `scripts/security/npm-audit-allowlist.txt` | 2026-11-04 |
| Sin alertado automático | El panel técnico muestra las colas atascadas, pero hay que entrar a mirarlo: no envía avisos | Alertar exigiría un canal externo (correo o webhook) y una política de umbrales; se deja fuera por no tener destinatario definido | Próxima iteración |
| Sin prueba de carga con concurrencia | Se analizaron planes de ejecución, no comportamiento bajo peticiones simultáneas | Fuera de alcance de una V2 sin alta disponibilidad ni escalado horizontal (RC-008, RC-009) | Si se plantea producción con carga real |
| Rate limiting en memoria | No funciona con varias instancias | Coherente con el despliegue de instancia única; introducir Redis exigiría ADR (ADR-0014) | Si se despliega más de una instancia |
| Token de verificación en el payload del Outbox | Quien pueda leer `outbox_message` puede secuestrar un alta sin verificar | Consecuencia de sacar el envío de la transacción (ADR-0012); acotado por el acceso a la base de datos | Próxima iteración |
| Ventana de consistencia del catálogo de ausencias | Unos segundos entre crear el tenant y tener sus tipos disponibles | Precio de no acoplar `tenant` con `absence`; se siembra por evento | Asumido |
| `POST /api/v1/app/absences` responde 200 | El resto de creaciones responden 201 | Inconsistencia de contrato, no fallo; cambiarla rompería el frontend | Próxima versión de la API |

## Estado de la CI

Siete jobs: `backend`, `frontend`, `docker`, `secret-scan`, `frontend-dependencies`,
`backend-dependencies` y `e2e`. Todos en verde salvo `backend-dependencies`, que
avisa y no bloquea mientras falte el secreto — decisión deliberada: hacerlo
fallar produciría un rojo permanente en cada PR, que es la vía más rápida para
que un equipo aprenda a ignorar la CI.

## Tareas del plan sin completar

Ninguna. Las dos que quedaban marcadas como opcionales (P3) —exportación PDF
(T100-06) y panel técnico (T140-05)— también están implementadas.
