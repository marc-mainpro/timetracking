## T502 — Frontend: gestión de empleados (admin)

### Cambios

- Se implementó `AdminEmployeesService` sobre la API real `/api/v1/employees`.
- Se sustituyó el placeholder de `/admin/employees` por una pantalla funcional con:
  - listado paginado
  - filtro visual por estado
  - búsqueda local por nombre/correo
  - alta de empleado
  - edición de perfil
  - reemplazo de roles
  - activar/desactivar con confirmación
- Los conflictos de backend (`EMAIL_ALREADY_IN_USE`, `LAST_ADMIN`) se traducen a mensajes claros sobre la acción/formulario.
- La ruta queda protegida por guard de autenticación + guard de rol `TENANT_ADMIN` ya operativo desde T404.

### Pruebas (comandos ejecutados y resultado)

```text
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless
cd frontend && npm run build
```

Resultado:

- **20 tests frontend verdes**
- **build Angular verde**

Cobertura nueva específica:

- componente admin con carga inicial
- validación de rol obligatorio
- manejo de `409 EMAIL_ALREADY_IN_USE`

### Cobertura

- La suite de frontend se amplió para cubrir también la pantalla administrativa real de empleados.

### Seguridad

- La pantalla depende de la protección por rol `TENANT_ADMIN`; un `EMPLOYEE` queda redirigido por guard.
- Las acciones críticas (desactivar/activar) requieren confirmación explícita del usuario.

### Documentación actualizada

- `tasks/STATUS.md`

### ADR

- No fue necesario crear ADR nueva.

### Riesgos detectados

1. No se verificó en esta tarea un flujo manual completo contra backend local más allá de la validación automática de build/tests.
2. La búsqueda visual es local sobre la página cargada; si el volumen crece, habrá que moverla a filtro de servidor en una iteración futura.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T502.
- El siguiente bloque natural es `T601`.
