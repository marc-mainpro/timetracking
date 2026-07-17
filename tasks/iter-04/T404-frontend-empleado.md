# T404 — Frontend: auth + dashboard de empleado

- Iteración: 4 · Depende de: T403, T102 · Contexto: CONTEXT-API (completo), CONTEXT-GLOBAL §6

## Objetivo
Frontend funcional para empleado: registro de organización, login y fichaje diario.

## Detalle
1. **Auth real**: `AuthService` (login/refresh/logout contra la API; access token SOLO en memoria; refresh vía cookie con `withCredentials`), interceptor Bearer + reintento único tras refresh en 401 (CONTEXT-API §4), guards reales de autenticación y de rol (sustituyen los placeholder de T102).
2. **Pantallas**: registro de organización (form reactivo: datos tenant + admin, validación espejo del servidor); login; dashboard de empleado con jornada actual (estado, hora inicio, pausas, duración en vivo) y botones Iniciar jornada / Iniciar pausa / Finalizar pausa / Finalizar jornada según estado; historial propio paginado con filtro de fechas.
3. Manejo global de errores: servicio que traduce Problem Details (`errorCode`) a mensajes claros en español; estados de carga y deshabilitado de botones durante peticiones; los 409 de transición se muestran como aviso, no como error técnico.

## Pruebas
Unitarias de `AuthService`, interceptor (incluido flujo de refresh) y guards; tests de componente de dashboard (estados: sin jornada, abierta, en pausa) con `HttpTestingController`.

## Fuera de alcance
Pantallas de admin (T502), correcciones (T605), informes (T802).

## Criterios de aceptación
- `ng build` y `ng test` verdes; flujo manual completo contra backend en Docker Compose verificado; refresh token jamás en `localStorage`/`sessionStorage`.

## Ficheros previstos
`frontend/src/app/core/**`, `frontend/src/app/features/{auth,employee-dashboard,workdays}/**`, tests.
