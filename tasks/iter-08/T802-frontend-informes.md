# T802 — Frontend: informes básicos

- Iteración: 8 · Depende de: T801, T502 · Contexto: CONTEXT-API §2 y §4

## Objetivo
Pantalla de informes para admin y resumen propio para empleado.

## Detalle
1. **Admin** (`/admin/reports`, guard de rol): selector de rango de fechas (form reactivo, validación from ≤ to), tabla de resumen por empleado (horas trabajadas, pausas, jornadas, ajustadas), botón "Exportar CSV" que descarga el fichero (blob + `Authorization` vía interceptor).
2. **Empleado**: sección de resumen propio (mismo rango de fechas) en su dashboard o vista dedicada.
3. Estados de carga y vacío ("sin datos en el rango"); errores 400 de rango mostrados en el formulario.

## Pruebas
Tests de componente con HTTP mock (tabla renderiza datos, validación de rango, descarga CSV invoca la URL correcta).

## Criterios de aceptación
- `ng build`/`ng test` verdes; verificación manual contra backend local.

## Ficheros previstos
`frontend/src/app/features/reports/**`, tests.
