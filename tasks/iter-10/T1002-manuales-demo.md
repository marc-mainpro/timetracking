# T1002 — Manuales y preparación de demo

- Iteración: 10 · Depende de: T1001 · Contexto: CONTEXT-GLOBAL

## Objetivo
Documentación de usuario/operación y guion de demo reproducible.

## Detalle
1. `docs/manuals/user-guide.md`: manual de usuario por rol (admin: alta organización, gestión de empleados, revisión de correcciones, informes; empleado: fichaje, pausas, historial, correcciones) con el flujo de cada pantalla.
2. `docs/manuals/operations.md`: manual de operación — arranque/parada, variables de entorno, backups de PostgreSQL, consulta de auditoría, gestión de mensajes outbox FAILED (retry manual), métricas disponibles, resolución de problemas comunes.
3. `docs/demo/demo-script.md`: guion paso a paso de demo (~10 min) que recorre los criterios de aceptación globales: dos tenants aislados, jornada completa con pausa, transición inválida rechazada, corrección aprobada y auditada, informe + CSV, outbox publicando. Incluir datos de ejemplo y, si aporta, script `scripts/seed-demo.sh` que los crea por API.
4. README raíz final: qué es el proyecto, arquitectura en 10 líneas con enlace a docs, cómo arrancar, cómo testear, estructura del repo.

## Criterios de aceptación
- Una persona sin contexto puede ejecutar la demo siguiendo el guion desde un clon limpio; manuales coherentes con el producto final.

## Ficheros previstos
`docs/manuals/**`, `docs/demo/**`, `scripts/seed-demo.sh`, `README.md`.
