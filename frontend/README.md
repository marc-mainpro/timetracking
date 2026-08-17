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
- La app se sirve con `LOCALE_ID: 'es'` (`app.config.ts`): sin registrarlo,
  `DatePipe` formatea en `en-US` (12 h con AM/PM, mes/día) y toda la interfaz
  está en español.
- **Barra superior en dos bandas** en escritorio (`app.component.*`): identidad
  y sesión arriba, navegación debajo, agrupada por ámbito (Mi tiempo /
  Administración / Plataforma / Cuenta) y separada por hairline. En móvil, la
  misma agrupación dentro de un **menú lateral escondible** (off-canvas +
  hamburguesa) que atrapa el foco mientras está abierto.
- **Pie común** (`shared/app-footer`) con la marca y la zona horaria del
  navegador: es la que interpreta las horas que se pintan.
- **Pantallas de acceso**: comparten `shared/auth-shell`, que pone la tarjeta,
  la cabecera de reloj (segundos atenuados) y la hairline cuyo relleno mide la
  fracción transcurrida del día. Las pantallas solo aportan su formulario.
- **Validación por campo**: cada control muestra su error bajo el campo con
  `<div class="validation">` cuando está `invalid && touched`, con mensaje
  concreto por tipo de error y `role="alert"` + `aria-invalid` +
  `aria-describedby` en el control. `.error` queda para el fallo global del
  envío (respuesta de la API), con la variante `.error--block` cuando es la
  única respuesta que recibe el usuario.
- Los tokens de `styles.scss` dan `padding` y `border-radius` a `.card`, así
  que las tarjetas no necesitan estilo local salvo que se salgan del patrón.

## Estructura

```
src/app/
  core/
    guards/        authGuard, roleGuard (control de acceso por rol)
    interceptors/  auth.interceptor (adjunta el token, reintenta con refresh)
    pipes/         iso-duration.pipe (formatea duraciones ISO-8601 del backend)
    services/      auth.service, error-messages.service, clock.service
  shared/
    auth-shell/    tarjeta y cabecera de reloj de las pantallas de acceso
    app-footer/    pie común (marca y zona horaria)
    brand/         lockup tipográfico de marca (TFP · Control horario)
  features/
    auth/                acceso, recuperar y restablecer contraseña
    registration/        solicitud de alta y verificación por correo
    employee-dashboard/  jornada actual del empleado (reloj en vivo)
    workdays/            historial de jornadas
    corrections/         correcciones (empleado) y cola de revisión (admin)
    absences/            ausencias (empleado) y resolución (admin)
    calendars/           calendarios laborales y asignaciones (admin)
    shifts/              turnos propios y plantillas/asignaciones (admin)
    notifications/       avisos del usuario
    reports/             informes de empleado y de tenant (export CSV y PDF)
    admin-employees/     gestión de empleados (admin)
    platform/            administración de plataforma (tenants y solicitudes)
  app.routes.ts        rutas con lazy loading y guards por rol
```

### Empleados por nombre

Las pantallas de administración (informes, ausencias, correcciones) muestran
al empleado por **apellidos y nombre**, nunca por su UUID: cargan el listado
de `/api/v1/employees` y resuelven el id contra él. Las exportaciones CSV y
PDF ya llegan del backend con el nombre resuelto.

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
