# Reservas y protocolo de trabajo en paralelo — V2

Este documento es **normativo** para cualquier agente que implemente una tarea de
la V2. Existe porque la V2 se desarrolla con varios agentes en paralelo, cada uno
en su propio git worktree, y sin reservas previas dos agentes elegirían el mismo
número de migración o reescribirían el mismo fichero compartido.

Léelo junto a `AGENTS.md` (reglas de arquitectura) y
`tareas-dependencias-v2-control-horario.md` (alcance de cada tarea).

---

## 1. Reserva de migraciones Flyway

Usa **solo** números de tu bloque. Si necesitas más, para y pídelo; no invadas el
bloque de otro. Está prohibido hacer `ALTER` sobre tablas que pertenecen a otro
agente de tu misma ola.

| Ola | Agente | Épica | Bloque |
|---|---|---|---|
| 1 | A1 | T53 registro público | V12–V13 |
| 1 | A2 | T30-03/04 rate limiting y bloqueo de cuentas | V14–V15 |
| 1 | A3a | T70 calendarios laborales | V16–V17 |
| 1 | A4 | T30-05 CI, T150 backups | — |
| 1 | A5 | T140 observabilidad | — |
| 2 | B1 | T60 sesiones y recuperación de contraseña | V18–V19 |
| 2 | B2 | T72 reglas horarias | V20–V21 |
| 2 | B3 | T90 turnos | V22–V23 |
| 2 | B4 | T110 notificaciones | V24–V25 |
| 2 | B5 | T130-04/05 auditoría de tenant | V26 |
| 3 | C1 | T80 ausencias | V27–V28 |
| 3 | C2 | integración del motor horario | V29 |
| 4 | D1 | T100 informes ampliados | V30–V31 |

Los huecos sin usar son aceptables: Flyway no exige numeración contigua.

## 2. Reserva de ADR

| Rango | Ola |
|---|---|
| 0011–0012 | Ola 0 (ya usados: contribución entre módulos, envío de correo) |
| 0013–0017 | Ola 1 |
| 0018–0021 | Ola 2 |
| 0022–0023 | Ola 3 |
| 0024+ | Olas 4–5 |

Elige el número dentro de tu rango y **no renumeres ADRs ajenos**. Formato: el de
`ADR-0010`, en castellano, con secciones Contexto y problema / Decisión /
Consecuencias / Alternativas descartadas.

## 3. Reserva de puertos (docker-compose)

| Puerto | Servicio |
|---|---|
| 5432 | postgres (no publicado al host) |
| 8080 | backend |
| 4200 | frontend |
| 8025 | mailpit (interfaz web) |
| 1025 | mailpit (SMTP, no publicado al host) |

Si necesitas un servicio nuevo, decláralo en tu `HANDOFF.md`; no edites
`docker-compose.yml`.

---

## 4. Ficheros PROHIBIDOS

No los edites. Declara lo que necesitas en tu `HANDOFF.md` y el agente principal
lo aplicará al mergear tu entrega.

| Fichero | Por qué |
|---|---|
| `docs/api/openapi.yaml` | Es **generado** por springdoc y exportado del endpoint `/v3/api-docs.yaml`. Cuida tus anotaciones `@Tag`, `@Operation` y `@Schema`; el principal reexporta una vez por ola. Editarlo a mano se perdería en la siguiente exportación. |
| `backend/pom.xml`, `frontend/package.json` y ambos lockfiles | Todas las dependencias de la V2 se declararon en la Ola 0. Si te falta una, para y pídela. |
| `docker-compose.yml`, `docker-compose.prod.yml`, `.env.example` | Merge a varias bandas con asignación de puertos. |
| `.github/workflows/ci.yml` | Solo el agente A4 lo toca en toda la V2. |
| `SecurityConfig` (orden de filtros) | Decisión global sobre la cadena (ADR-0011). Tus rutas públicas van en tu propio `PublicEndpointsContributor`. |
| `application.yml` | Ya declara el `spring.config.import` de tu fichero. |

## 5. Ficheros COMPARTIDOS con convención

Puedes editarlos, pero **solo añadiendo**, en orden determinista y sin tocar lo
ajeno. Un reformateo convierte un merge limpio en un conflicto de todo el fichero.

| Fichero | Convención |
|---|---|
| `frontend/src/app/app.routes.ts` | Inserta tu ruta manteniendo el **orden alfabético por `path`** |
| `frontend/src/app/app.component.html` | Añade tu enlace en el bloque del rol que corresponda |
| `docs/traceability/requirements-matrix.md` | Solo filas nuevas, ordenadas por id de requisito. No reformatees tablas ajenas |
| `docs/integration/event-catalog.md` | Añade tu sección `###` al final de «Tipos de evento», con el formato exacto de las existentes |
| `docs/acceptance-checklist.md` | Una viñeta nueva en la sección que corresponda, con la evidencia (clase de test) |
| `docs/security/threat-model.md` | Una sección `##` nueva al final; no edites secciones ajenas |
| Tests de `architecture/` | Puedes añadir ficheros nuevos; **los 7 existentes son intocables** |

## 6. Puntos de contribución (usa estos, no edites listas centrales)

Ver ADR-0011.

| Necesitas… | Implementa… | En… |
|---|---|---|
| Publicar un evento de integración | `IntegrationEventMapper` (`@Component`) | `<módulo>.application.integration` |
| Abrir un endpoint sin autenticación | `PublicEndpointsContributor` (`@Component`) | `<módulo>.infrastructure` |
| Configuración propia | un `config/<feature>.yml` ya declarado en `application.yml` | `backend/src/main/resources/config/` |
| Enviar correo | inyecta el puerto `EmailSender` | `notification.application` |

Si tu funcionalidad necesita un `config/*.yml` que no esté en la lista de
imports, decláralo en tu `HANDOFF.md`.

## 7. El `HANDOFF.md`

Deja un `HANDOFF.md` en la raíz de tu worktree con:

1. **Ficheros prohibidos**: las líneas exactas que necesitas que se añadan.
2. **Configuración**: propiedades, variables de entorno y puertos nuevos.
3. **Eventos**: los `eventType` que publicas y quién debería consumirlos.
4. **Riesgos**: lo que has dejado a medias, asumido o que puede chocar con otro
   agente.

## 8. Definition of Done

De `tareas-dependencias-v2-control-horario.md` §28. Tu entrega no está lista sin:

- Tests unitarios de toda regla de negocio.
- Tests de integración con Testcontainers para persistencia, seguridad y Outbox.
- **Tests cross-tenant** de toda consulta o endpoint nuevo (RT-003).
- Tests por rol: permitido y prohibido (RT-004).
- ArchUnit en verde y cobertura que no baja (90 % dominio / 80 % aplicación).
- Migración dentro de tu bloque, probada desde base limpia.
- ADR dentro de tu rango si tomas una decisión arquitectónica.
- Fila en la matriz de trazabilidad y entrada en el catálogo de eventos.
- Sin secretos y sin `tenant_id` procedente del cliente.
