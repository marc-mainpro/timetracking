# T605 — Frontend: solicitud y revisión de correcciones

- Iteración: 6 · Depende de: T602, T502 · Contexto: CONTEXT-API §2 y §4

## Objetivo
Pantallas de corrección para empleado (solicitar) y admin (revisar).

## Detalle
1. **Empleado**: desde el historial (T404), acción "Solicitar corrección" sobre una jornada cerrada → formulario reactivo (motivo obligatorio, nuevos horarios y pausas propuestos con validación temporal en cliente); listado de sus solicitudes con estado.
2. **Admin**: ruta `/admin/corrections` (guard de rol): listado filtrable por estado, detalle con diff visual jornada actual vs. propuesta, acciones aprobar (comentario opcional) / rechazar (comentario obligatorio) con confirmación.
3. Manejo de 409 (`CORRECTION_ALREADY_PENDING`, `CORRECTION_ALREADY_RESOLVED`, `CONCURRENT_MODIFICATION`) con mensajes claros y recarga del estado.

## Pruebas
Tests de componente de ambos flujos con HTTP mock; validaciones de formulario.

## Criterios de aceptación
- `ng build`/`ng test` verdes; flujo manual empleado-solicita → admin-aprueba verificado contra backend local.

## Ficheros previstos
`frontend/src/app/features/corrections/**`, ampliaciones en `workdays`, tests.
