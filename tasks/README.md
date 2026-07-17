# Plan de tareas — MVP SaaS Control Horario

Descomposición del `SDD-MVP-control-horario.md` en tareas ejecutables por subagentes económicos (p. ej. Haiku/Sonnet). Cada tarea es autocontenida: el subagente solo necesita leer los ficheros de contexto indicados y su ficha de tarea.

## Cómo lanzar una tarea a un subagente

Prompt plantilla:

```text
Trabaja en /home/marc/Repos/TFP/P1.

1. Lee tasks/_context/CONTEXT-GLOBAL.md y cúmplelo en todo momento.
2. Lee los contextos adicionales listados en la cabecera de la tarea.
3. Lee tasks/iter-XX/TXXX-<nombre>.md y ejecuta EXACTAMENTE esa tarea.

Reglas:
- No amplíes el alcance: lo marcado como "Fuera de alcance" NO se implementa.
- No marques la tarea como terminada si los tests o el build fallan.
- Al terminar, escribe un resumen en tasks/_reports/TXXX-report.md con el
  formato "Salida de tarea" definido en CONTEXT-GLOBAL.md.
```

## Contexto compartido

| Fichero | Contenido | Quién lo lee |
|---|---|---|
| `_context/CONTEXT-GLOBAL.md` | Stack, arquitectura, multitenancy, seguridad, errores, DoD, decisiones fijadas | **Todas** las tareas |
| `_context/CONTEXT-DOMINIO.md` | Entidades, invariantes, estados, eventos de dominio e integración | Tareas de backend con lógica de negocio |
| `_context/CONTEXT-API.md` | Endpoints, contratos, Problem Details, convenciones REST | Tareas con endpoints o frontend |

## Orden y dependencias

Ejecutar iteraciones en orden. Dentro de una iteración, las tareas sin dependencia mutua pueden ir en paralelo.

```text
Iter 1  T101 backend ─┬─ T103 docker/flyway ── T104 CI
        T102 frontend ┘   T105 docs/ADR/AGENTS   T106 archunit (tras T101)
Iter 2  T201 migración ── T202 dominio ── T203 register-tenant ── T204 auth ── T205 tests seguridad
Iter 3  T301 tenant-context ── T302 repos tenant-aware ── T303 tests cross-tenant
Iter 4  T401 dominio workday ── T402 migración ── T403 casos de uso+API ── T404 frontend empleado
Iter 5  T501 gestión empleados API ── T502 frontend admin
Iter 6  T601 dominio corrección ── T602 casos de uso+API ── T603 auditoría ── T604 concurrencia ── T605 frontend
Iter 7  T701 migración outbox ── T702 escritura atómica ── T703 publicador ── T704 tests+catálogo
Iter 8  T801 informes API ── T802 frontend informes
Iter 9  T901 E2E ── T902 hardening ── T903 cobertura+docs
Iter 10 T1001 dockerización ── T1002 manuales+demo
```

Paralelizables típicos: T101‖T102, T105‖T103, T404‖T501, T605‖T701, T802‖T901.

## Estado

Mantener el estado en `tasks/STATUS.md` (una línea por tarea: `TXXX | pendiente/en curso/hecha | notas`). El orquestador lo actualiza al recibir cada informe.
