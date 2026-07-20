# Manual de usuario

## Admin del tenant

1. Registro inicial: acceder a `/auth/register`, crear organización y primer
   administrador.
2. Gestión de empleados: ir a `/admin/employees`, listar, crear, editar,
   activar/desactivar y cambiar roles.
3. Correcciones: ir a `/admin/corrections`, revisar solicitudes pendientes,
   aprobar o rechazar con comentario.
4. Informes: ir a `/admin/reports`, elegir rango, revisar tabla agregada y
   descargar CSV.
5. Auditoría: consultar `GET /api/v1/admin/audit-events` desde Swagger UI o la
   API para ver aprobaciones/rechazos auditados.

## Empleado

1. Login: acceder a `/auth/login`.
2. Dashboard: en `/employee-dashboard` ver el estado actual de jornada.
3. Fichaje: desde `/workdays`, iniciar jornada, iniciar/finalizar pausa y
   cerrar jornada.
4. Historial: en la misma pantalla revisar jornadas previas.
5. Correcciones: en `/corrections`, abrir una jornada cerrada, proponer ajuste
   y seguir su estado.
6. Informe propio: en `/reports`, consultar resumen diario de horas.
