## T102 — Scaffolding del frontend Angular

### Cambios
- Creado proyecto Angular standalone en `frontend/` con Angular CLI **19.2** (compatible con Node 20.20.2 / npm 10.8.2), TypeScript estricto (`strict: true` + flags adicionales de `tsconfig.json` generados por el CLI), sin NgModules.
- Estructura por features bajo `frontend/src/app/`:
  - `core/services/auth.service.ts` (esqueleto, sin lógica real).
  - `core/interceptors/auth.interceptor.ts` (paso a través, `TODO T204`).
  - `core/guards/auth.guard.ts` (deja pasar siempre, `TODO T204`/`TODO T404`).
  - `shared/components/` (carpeta preparada, sin componentes aún — fuera de alcance).
  - `features/auth/login.component.*` + `auth.routes.ts`.
  - `features/employee-dashboard/employee-dashboard.component.*` + rutas.
  - `features/workdays/workdays.component.*` + rutas.
  - `features/corrections/corrections.component.*` + rutas.
  - `features/admin-employees/admin-employees.component.*` + rutas.
  - `features/reports/reports.component.*` + rutas.
- `app.routes.ts`: ruta pública `auth/login` (lazy) + resto de features cargadas con `loadChildren` (lazy) tras `authGuard` placeholder (comentado con `TODO T204/T404`); redirección `''` → `auth/login` y wildcard → `auth/login`.
- `app.config.ts`: `provideRouter`, `provideHttpClient(withInterceptors([authInterceptor]))`, `provideZoneChangeDetection`.
- `app.component.html/.ts`: shell mínimo con `<nav>` de enlaces a cada ruta placeholder y `<router-outlet>` (se sustituyó el template de bienvenida por defecto del CLI).
- `src/environments/environment.ts` y `environment.production.ts` con `apiBaseUrl: '/api/v1'`; `fileReplacements` añadido en `angular.json` para la build de producción.
- `frontend/proxy.conf.json` apuntando a `http://localhost:8080`; `angular.json` → `serve.options.proxyConfig` configurado.
- ESLint configurado vía `ng add @angular-eslint/schematics` (genera `eslint.config.js` y target `lint` en `angular.json`).
- Tests de humo: `app.component.spec.ts` ampliado con caso de navegación entre rutas placeholder (usa `provideRouter(routes)` en el TestBed) además de los specs por defecto de cada componente placeholder generados por el CLI.

### Pruebas (comandos ejecutados y resultado)
- `npm ci` → OK (1115 paquetes instalados; únicamente avisos de deprecación de subdependencias del propio Angular CLI/Karma, sin errores).
- `npx ng build` → OK, build de producción completa (`Application bundle generation complete`), incluye 6 chunks lazy (uno por feature).
- `npx ng lint` → `All files pass linting.`
- `npx ng test --watch=false --browsers=ChromeHeadless` → `TOTAL: 9 SUCCESS` (Chrome Headless 150.0.0.0, ejecutado dos veces: una con `CHROME_BIN=/usr/bin/google-chrome` y otra sin la variable, ambas en verde).
- Se repitió el ciclo completo `rm -rf node_modules dist && npm ci && ng build && ng test --watch=false --browsers=ChromeHeadless` de principio a fin para confirmar reproducibilidad: verde.

Navegador para Karma: el entorno tiene `google-chrome` instalado en `/usr/bin/google-chrome` (`google-chrome-stable`). `karma-chrome-launcher` lo detecta automáticamente sin configuración adicional; no hizo falta tocar `karma.conf.js` ni fijar `CHROME_BIN` en scripts. Si en otro entorno (p. ej. CI) Chrome estuviera en otra ruta o no estuviera en el PATH, basta con exportar `CHROME_BIN=/ruta/al/binario` antes de `npm test`; karma-chrome-launcher lee esa variable de entorno de forma nativa.

### Cobertura
No aplica (sin lógica de negocio; solo componentes placeholder y specs de humo generados por el CLI). No se ha configurado umbral de cobertura para este scaffolding.

### Seguridad
- `authGuard` y `authInterceptor` son no-op deliberados (marcados `TODO T204`/`TODO T404`); no hay tokens, llamadas HTTP reales ni almacenamiento de credenciales en este scaffolding, conforme a "Fuera de alcance" de la ficha.
- Sin secretos en el repositorio; `environment.ts`/`environment.production.ts` solo contienen `apiBaseUrl` (no secretos).

### Documentación actualizada
Ninguna en `docs/` (no aplica a esta tarea; no se ha modificado la API ni contratos).

### ADR
No se ha tomado ninguna decisión fuera de las ya fijadas en CONTEXT-GLOBAL/CONTEXT-API. Angular 19.2 se eligió por ser la versión estable más reciente compatible con Node 20.20.2 dentro del rango permitido (19 o 20) por la propia consigna de la tarea; no requiere ADR nuevo (ya está dentro de lo fijado en CONTEXT-GLOBAL §2 "Angular (standalone components)").

### Riesgos detectados
- `npm audit` reporta 28 vulnerabilidades (2 low, 10 moderate, 16 high) en dependencias transitivas del toolchain de build/test (Angular CLI/Karma/etc.), no en código propio. No se ha ejecutado `npm audit fix --force` para no introducir cambios de versión no solicitados; queda pendiente de revisión por el equipo si se quiere endurecer el pipeline de CI.
- El guard de autenticación y el interceptor son intencionadamente no-op; cualquier prueba manual de navegación mostrará todas las rutas accesibles sin login real hasta T204/T404.

### Pendientes / decisiones que necesitan humano
- Ninguna decisión bloqueante. Pendiente de implementación en tareas futuras: T204 (lógica real de autenticación, interceptor y guard) y T404 (guard de rol).
