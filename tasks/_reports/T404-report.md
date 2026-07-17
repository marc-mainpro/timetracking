## T404 — Frontend: auth + dashboard de empleado

### Cambios

- Se sustituyeron los placeholders de `auth` por integración real contra backend:
  - `AuthService` con login/refresh/logout reales
  - access token solo en memoria
  - refresh token siempre por cookie `HttpOnly`
- Se añadió interceptor Bearer con reintento único tras `401` vía `/api/v1/auth/refresh`.
- Se implementaron guards reales:
  - autenticación
  - rol por ruta
- Se añadió `ErrorMessagesService` para traducir Problem Details a mensajes claros en español.
- Se creó la pantalla de registro de organización.
- Se implementó el dashboard de empleado con:
  - jornada actual
  - duración en vivo
  - botones de iniciar jornada / iniciar pausa / finalizar pausa / finalizar jornada según estado
- Se implementó el historial propio paginado con filtro de fechas.
- Se actualizó la navegación principal para reflejar sesión autenticada y logout.

### Pruebas (comandos ejecutados y resultado)

```text
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless
cd frontend && npm run build
```

Resultado:

- **18 tests frontend verdes**
- **build Angular verde**

Cobertura funcional nueva:

- `AuthService`
- interceptor de auth con refresh
- guards de auth/rol
- dashboard en estados: sin jornada, abierta, en pausa

### Cobertura

- Se ampliaron los tests unitarios/componentes del scaffold inicial para cubrir el flujo real de autenticación y jornadas del empleado.

### Seguridad

- El refresh token no se persiste en `localStorage` ni `sessionStorage`.
- El access token vive solo en memoria.
- Las rutas de empleado quedan protegidas por auth + rol `EMPLOYEE`.

### Documentación actualizada

- `tasks/STATUS.md`

### ADR

- No fue necesario crear ADR nueva.

### Riesgos detectados

1. No se verificó en esta tarea un flujo manual completo contra backend levantado en Docker Compose; sí quedaron validados los contratos mediante tests de frontend y el backend ya estaba cubierto por integración real.
2. El historial usa conversión a ISO UTC desde inputs `datetime-local`; es suficiente para el MVP actual, pero habrá que revisar la UX de fechas cuando entren zonas horarias por tenant en frontend más avanzado.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T404.
- La siguiente tarea natural es `T502` para el frontend admin de empleados.
