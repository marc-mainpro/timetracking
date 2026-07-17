## T605 — Frontend: solicitud y revisión de correcciones

### Cambios

- Se implementó `corrections.service.ts` con soporte para:
  - listado y detalle de correcciones
  - solicitud de corrección por jornada
  - aprobación y rechazo
  - carga de jornada propia y jornada admin para comparación visual
- Se convirtió `CorrectionsComponent` en la pantalla real de empleado:
  - formulario reactivo para solicitar corrección
  - validación temporal en cliente para rango de jornada y pausas
  - listado de solicitudes propias con estado y comentario de resolución
  - carga desde `queryParam workdayId` para abrir el flujo desde historial
- Se añadió `AdminCorrectionsComponent` para `/admin/corrections` con:
  - listado filtrable por estado
  - detalle con comparación visual jornada actual vs propuesta
  - acciones aprobar/rechazar con confirmación
  - recarga de estado tras `CORRECTION_ALREADY_RESOLVED` o `CONCURRENT_MODIFICATION`
- Se añadió la acción `Solicitar corrección` en el historial de jornadas cerradas.
- Se protegieron rutas y navegación:
  - `/corrections` solo para `EMPLOYEE`
  - `/admin/corrections` solo para `TENANT_ADMIN`
  - navegación superior separa enlaces de empleado y admin
- `ErrorMessagesService` ahora cubre:
  - `CORRECTION_ALREADY_PENDING`
  - `CORRECTION_ALREADY_RESOLVED`
  - `CONCURRENT_MODIFICATION`

### Pruebas (comandos ejecutados y resultado)

```text
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless
cd frontend && npm run build
```

Resultado:

- **Tests OK**. `25 SUCCESS`.
- **Build OK**. `ng build` verde.

Cobertura nueva específica:

- validación de formulario de solicitud de corrección en empleado
- manejo de conflicto `CORRECTION_ALREADY_PENDING`
- validación de comentario obligatorio para rechazo admin
- recarga de estado ante `CONCURRENT_MODIFICATION` en revisión admin

### Cobertura

- La suite frontend se mantiene en verde tras añadir las pantallas y tests de correcciones.

### Seguridad

- El flujo empleado queda protegido por rol `EMPLOYEE`.
- El flujo admin queda protegido por rol `TENANT_ADMIN`.
- Los conflictos `409` se traducen a mensajes claros y recarga del estado más reciente.

### Documentación actualizada

- No fue necesario tocar documentación adicional fuera del propio código/rutas.

### ADR

- No fue necesario crear ADR nueva.

### Riesgos detectados

1. La verificación manual completa empleado-solicita -> admin-aprueba contra backend local no se ejecutó en esta sesión; la validación quedó en tests de componente + build.

### Pendientes / decisiones que necesitan humano

- Verificar manualmente el flujo completo en navegador contra backend local si se quiere cubrir el criterio manual literal.
- La siguiente tarea correcta es `T701`.
