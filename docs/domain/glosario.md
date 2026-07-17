# Glosario

- **Tenant**: organización cliente del SaaS. Todos los datos de negocio están
  aislados por `tenant_id`.
- **User**: persona autenticable perteneciente a un único tenant, con rol
  `TENANT_ADMIN` o `EMPLOYEE`.
- **Workday (jornada)**: periodo de trabajo de un empleado en un día,
  agregado raíz que contiene sus pausas.
- **BreakEntry (pausa)**: intervalo de pausa dentro de una jornada.
- **CorrectionRequest (corrección)**: solicitud de modificación de una
  jornada ya existente, sujeta a aprobación.
- **Auditoría**: registro inmutable de operaciones sensibles (p. ej.
  resolución de correcciones).
- **Evento de dominio**: hecho pasado e inmutable generado por un agregado.
- **Evento de integración**: versión pública y versionada de un evento de
  dominio, publicada vía Outbox para consumidores externos.
- **Outbox (Transactional Outbox)**: patrón que persiste los eventos de
  integración en la misma transacción que el cambio de negocio, para
  garantizar entrega at-least-once sin broker.
- **TenantContext**: mecanismo que resuelve el tenant activo a partir del
  usuario autenticado, nunca de un parámetro enviado por el cliente.
