# T102 — Scaffolding del frontend Angular

- Iteración: 1 · Depende de: — · Contexto: CONTEXT-GLOBAL, CONTEXT-API §4

## Objetivo
Crear el proyecto Angular base con routing, estructura por features y tests de humo en verde.

## Detalle
1. Crear `frontend/` con Angular CLI (standalone components, sin NgModules), TypeScript estricto.
2. Estructura: `src/app/core/` (servicios auth, interceptores, guards — vacíos o esqueleto), `src/app/shared/` (componentes comunes), `src/app/features/` con carpetas `auth`, `employee-dashboard`, `workdays`, `corrections`, `admin-employees`, `reports` (placeholder con componente vacío y ruta).
3. Rutas definidas con lazy loading por feature; página de login placeholder como ruta pública, resto tras guard placeholder (que de momento deja pasar, marcado con TODO T204/T404).
4. `environment.ts` con `apiBaseUrl` configurable; proxy de desarrollo a `http://localhost:8080`.
5. Linter (eslint) configurado; `ng test` en modo headless funcional.

## Fuera de alcance
Lógica real de auth, pantallas funcionales, llamadas HTTP reales.

## Criterios de aceptación
- `npm ci && ng build && ng test --watch=false --browsers=ChromeHeadless` en verde.
- Navegación entre rutas placeholder funciona.

## Ficheros previstos
`frontend/**` (proyecto completo), `frontend/proxy.conf.json`.
