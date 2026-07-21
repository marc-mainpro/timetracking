# Frontend — SPA de control horario

Aplicación web del MVP de control horario. **Angular 19** (standalone
components + signals), servida en producción por **nginx** con una CSP
estricta. Consume la API REST del backend bajo `/api/v1`.

> Contexto general del producto y arranque de todo el stack: [`../README.md`](../README.md).
> API: Swagger UI del backend en `http://localhost:8080/swagger-ui.html`.

## Stack

- Angular 19 (componentes standalone, señales, router con lazy loading).
- TypeScript 5.7, RxJS 7.8.
- Tipografías auto-hospedadas con `@fontsource` (Inter + IBM Plex Mono),
  necesarias para cumplir la CSP `font-src 'self'` de nginx.
- Karma + Jasmine (tests), ESLint (lint).

## Diseño e interfaz

- Lenguaje visual **minimalista y mobile-first**: paleta clara neutra,
  tarjetas planas con hairline, Inter para UI e IBM Plex Mono con cifras
  tabulares para el reloj y las duraciones. Los tokens compartidos viven en
  `src/styles.scss`.
- Todas las páginas se maquetan desde móvil hacia arriba (base en una columna,
  rejillas que se abren con `min-width`).
- Navegación con **menú lateral escondible** (off-canvas + hamburguesa) en
  móvil y barra en línea en escritorio (`app.component.*`).

## Estructura

```
src/app/
  core/
    guards/        authGuard, roleGuard (control de acceso por rol)
    interceptors/  auth.interceptor (adjunta el token, reintenta con refresh)
    pipes/         iso-duration.pipe (formatea duraciones ISO-8601 del backend)
    services/      auth.service, error-messages.service
  features/
    auth/                login y registro de organización
    employee-dashboard/  jornada actual del empleado (reloj en vivo)
    workdays/            historial de jornadas
    corrections/         correcciones (empleado) y cola de revisión (admin)
    reports/             informes de empleado y de tenant (con export CSV)
    admin-employees/     gestión de empleados (admin)
  app.routes.ts        rutas con lazy loading y guards por rol
```

### Acceso por rol

Tras el login, el usuario se enruta según su rol: `TENANT_ADMIN` a
`/admin/employees` y `EMPLOYEE` a `/employee-dashboard`. `roleGuard` protege
cada ruta y el `authGuard` exige sesión activa.

### Duraciones

El backend serializa las duraciones como ISO-8601 (`java.time.Duration`, p. ej.
`PT7.66S`). El pipe `isoDuration` las formatea en plantilla:

```html
{{ workday.workedDuration | isoDuration }}        <!-- 00:00:07 (HH:MM:SS) -->
{{ row.worked | isoDuration: 'hm' }}              <!-- 08:30 (HH:MM) -->
{{ row.worked | isoDuration: 'long' }}            <!-- 8h 30min -->
```

## Requisitos

- Node.js 20+ y npm.

```bash
npm install
```

## Desarrollo

```bash
npm start
```

Sirve en `http://localhost:4200` con recarga en caliente. Las peticiones a
`/api` se redirigen al backend `http://localhost:8080` mediante
`proxy.conf.json`, así que necesitas el backend levantado (o el `docker
compose` de la raíz).

## Scripts

| Comando | Descripción |
| --- | --- |
| `npm start` | Servidor de desarrollo (`ng serve`). |
| `npm run build` | Build de producción en `dist/`. |
| `npm test` | Tests unitarios (Karma + Jasmine). |
| `npm run test:coverage` | Tests headless con cobertura. |
| `npm run lint` | ESLint. |

## Build y CSP

`npm run build` genera un build de producción optimizado. Dos ajustes son
necesarios para la CSP estricta servida por nginx (`nginx.conf`):

- **Fuentes auto-hospedadas** (`@fontsource`): se empaquetan como assets
  propios (`./media/*.woff2`), servidos desde `self`. No se usa Google Fonts,
  que la CSP bloquea.
- **`optimization.styles.inlineCritical: false`** en `angular.json` (config
  `production`): evita el `<link ... onload="…">` inline que violaría
  `script-src 'self'`.

## Imagen Docker

`Dockerfile` multi-stage (build de Angular + nginx). Se construye desde la raíz
con `docker compose build frontend`. En producción, nginx sirve el SPA y hace
proxy de `/api/` hacia el backend.
