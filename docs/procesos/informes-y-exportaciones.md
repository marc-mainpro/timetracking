# Informes y exportaciones

Los informes agregan las jornadas registradas para responder a dos preguntas
distintas: *«¿qué he trabajado yo cada día?»* (informe por empleado) y *«¿qué ha
trabajado cada persona de mi organización?»* (informe agregado del tenant). Este
último se puede además descargar en CSV o en PDF.

Es un proceso de **solo lectura**: no cambia estados, no publica eventos y no
escribe auditoría. Lo interesante está en dos sitios: cómo se decide quién puede
ver qué, y cómo se garantiza que los cuatro formatos digan lo mismo.

## Actores

| Actor | Responsabilidad |
| --- | --- |
| `EMPLOYEE` | Consulta su propio resumen diario. |
| `TENANT_ADMIN` | Consulta el resumen de cualquier empleado de su tenant y el agregado, y lo exporta. |

## Diagrama de secuencia

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant B as Backend
    participant BD as Base de datos

    rect rgb(240, 244, 248)
    Note over U,BD: Informe de un empleado
    U->>B: GET /api/v1/reports/employees/{id}/summary?from=&to=
    alt Rango de fechas inválido
        B-->>U: 400
    else Es EMPLOYEE y pide el de otra persona
        B-->>U: 404 (nunca 403)
    else El empleado no existe o es de otro tenant
        B-->>U: 404
    else
        B->>BD: Jornadas del empleado en el rango
        B->>B: Agrega por día, en la zona horaria del tenant
        B-->>U: Resumen diario
    end
    end

    rect rgb(244, 240, 248)
    Note over U,BD: Informe del tenant y exportaciones
    U->>B: GET /api/v1/reports/tenant/summary?from=&to=
    B->>BD: Jornadas del tenant en el rango
    B->>B: Agrega totales por empleado
    B-->>U: Resumen por empleado

    U->>B: GET /api/v1/reports/tenant/export.csv
    Note over B: Misma agregación,<br/>solo cambia el formateador
    B-->>U: text/csv como descarga

    U->>B: GET /api/v1/reports/tenant/export.pdf
    B->>BD: Nombres de los empleados del tenant
    B-->>U: application/pdf como descarga
    end
```

## Autorización: dos capas, no una

El rol se comprueba con `@PreAuthorize` en el controlador, pero eso solo dice
*qué tipo de usuario* puede llamar al endpoint. Sobre **qué datos** puede verse
se decide dentro del caso de uso:

```mermaid
flowchart TD
    A["GET /reports/employees/{id}/summary"] --> B{¿Es TENANT_ADMIN?}
    B -->|Sí| C[Puede pedir el de cualquier<br/>empleado de su tenant]
    B -->|No| D{¿El id es el suyo propio?}
    D -->|Sí| C
    D -->|No| E[404 Empleado no encontrado]
    C --> F{¿Existe ese empleado<br/>en su tenant?}
    F -->|No| E
    F -->|Sí| G[Se genera el informe]
```

La respuesta es `404` y no `403` por la misma razón que en el resto del sistema:
un `403` confirmaría que ese empleado existe. Igual que en jornadas y
correcciones (ADR-0002).

## Una sola agregación, cuatro presentaciones

El informe del tenant, el CSV y el PDF **comparten el mismo caso de uso de
cálculo**. El formato es una decisión de presentación, no una consulta distinta:
duplicar la agregación abriría la puerta a que la web y el PDF dejaran de
cuadrar entre sí, que es exactamente el tipo de discrepancia que nadie detecta
hasta que alguien la usa para nóminas.

```mermaid
flowchart LR
    Q[Jornadas del tenant<br/>en el rango] --> AG[Agregación de totales<br/>por empleado]
    AG --> W[Respuesta JSON]
    AG --> C[Formateador CSV]
    AG --> P[Renderizador PDF]
    D[Directorio de nombres<br/>de empleados] --> P
```

El PDF necesita además los nombres de los empleados, que obtiene de un puerto de
directorio; el resto de formatos trabaja solo con identificadores.

## Zona horaria

El resumen por empleado agrega **por día local del tenant**, no por día UTC. Una
jornada que empieza a las 23:30 pertenece al día que el empleado percibe como
suyo, no al día siguiente en UTC. El informe agregado del tenant no lo necesita:
suma totales del rango completo, sin desglose diario.

## Endpoints implicados

| Método | Ruta | Rol | Descripción |
| --- | --- | --- | --- |
| `GET` | `/api/v1/reports/employees/{id}/summary` | `EMPLOYEE`, `TENANT_ADMIN` | Resumen diario de un empleado. |
| `GET` | `/api/v1/reports/tenant/summary` | `TENANT_ADMIN` | Totales por empleado del tenant. |
| `GET` | `/api/v1/reports/tenant/export.csv` | `TENANT_ADMIN` | Mismo dato en `text/csv`, como descarga. |
| `GET` | `/api/v1/reports/tenant/export.pdf` | `TENANT_ADMIN` | Mismo dato en PDF, como descarga. |

Los tres parámetros `from` y `to` son instantes ISO-8601 obligatorios. Las
exportaciones se sirven con `Content-Disposition: attachment`.

## Ramas de error y reglas

| Condición | Resultado |
| --- | --- |
| Rango de fechas inválido (invertido o fuera de límites) | `400`. |
| Un `EMPLOYEE` pide el informe de otro | `404`, nunca `403`. |
| El `employeeId` es de otro tenant | `404`. |
| Un `EMPLOYEE` llama a los endpoints de tenant | `403` por rol: aquí sí, porque no hay recurso concreto cuya existencia se pudiera filtrar. |

## Efectos

Ninguno. Es un proceso de solo lectura: no publica eventos, no escribe
auditoría y no modifica estado.

## Frontend

| Pantalla | Ruta | Papel |
| --- | --- | --- |
| Mi informe | `/reports` | Formulario de rango y resumen diario propio. |
| Informes del tenant | `/admin/reports` | Resumen por empleado y botones de descarga CSV y PDF, que se obtienen como blob. |

Utilidades de formato de duraciones: `frontend/src/app/features/reports/duration.util.ts`
y `core/pipes/iso-duration.pipe.ts`.

## Referencias

- ADR-0002 — `404` para recursos de otro tenant o de otro empleado
- `docs/api/openapi.yaml`
- Backend: `reporting/interfaces/rest/ReportController.java`,
  `reporting/application/GenerateEmployeeTimeSummaryUseCase.java`,
  `GenerateTenantTimeSummaryUseCase.java`, `ExportTimeSummaryCsvUseCase.java`,
  `ExportTimeSummaryPdfUseCase.java`,
  `reporting/domain/TimeSummaryCalculator.java`,
  `reporting/infrastructure/TimeSummaryPdfWriter.java`
