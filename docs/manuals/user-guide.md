# Manual de usuario

## Admin del tenant

1. Registro inicial: acceder a `/registro`, enviar la solicitud de alta y esperar a que plataforma la apruebe; crea la organización y su primer
   administrador.
2. Gestión de empleados: ir a `/admin/employees`, listar, crear, editar,
   activar/desactivar y cambiar roles.
3. Correcciones: ir a `/admin/corrections`, revisar solicitudes pendientes,
   aprobar o rechazar con comentario.
4. Informes: ir a `/admin/reports`, elegir rango, revisar tabla agregada y
   descargar CSV o PDF. Tanto la tabla como los ficheros identifican a cada
   empleado por apellidos y nombre.
5. Ausencias: ir a `/admin/absences`, revisar las solicitudes del tenant y
   aprobarlas o rechazarlas.
6. Calendarios: ir a `/admin/calendars`, definir calendarios laborales con
   festivos y jornadas especiales, y asignarlos por tenant, equipo o empleado.
7. Turnos: ir a `/admin/shifts`, crear plantillas (incluidas las que cruzan
   medianoche) y asignarlas a empleados.
8. Auditoría: consultar `GET /api/v1/admin/audit-events` desde Swagger UI o la
   API para ver aprobaciones/rechazos auditados.

## Admin de plataforma

1. Tenants: en `/platform`, consultar organizaciones y su ciclo de vida.
2. Solicitudes de alta: revisar las solicitudes pendientes y aprobarlas o
   rechazarlas; la aprobación crea el tenant y su primer administrador.

## Empleado

1. Login: acceder a `/auth/login`. Si no recuerdas la contraseña, usa «¿Has
   olvidado tu contraseña?»: pide un enlace en `/auth/recuperar-contrasena` y el
   correo te lleva a `/restablecer-contrasena`, donde eliges una nueva. El enlace
   solo puede usarse una vez, caduca en el plazo que indique el propio correo
   (`AUTH_PASSWORD_RESET_TOKEN_TTL`, una hora por defecto) y al usarlo se cierran
   las sesiones abiertas en otros dispositivos.
2. Dashboard: en `/employee-dashboard` ver el estado actual de jornada.
3. Fichaje: desde `/workdays`, iniciar jornada, iniciar/finalizar pausa y
   cerrar jornada.
4. Historial: en la misma pantalla revisar jornadas previas.
5. Correcciones: en `/corrections`, abrir una jornada cerrada, proponer ajuste
   y seguir su estado.
6. Ausencias: en `/absences`, solicitar una ausencia, seguir su estado o
   cancelarla mientras siga pendiente.
7. Turnos: en `/shifts`, consultar los turnos asignados.
8. Notificaciones: en `/notifications`, revisar los avisos recibidos.
9. Informe propio: en `/reports`, consultar resumen diario de horas.
