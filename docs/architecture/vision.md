# Visión

## Problema

Las organizaciones necesitan controlar el horario de sus empleados (entrada,
pausas, salida), corregir errores de fichaje de forma auditable y disponer de
informes básicos, cumpliendo con la normativa de registro de jornada.

## Solución

MVP SaaS **multitenant** de control horario. Cada organización (tenant)
gestiona de forma aislada a sus empleados y sus jornadas. Los empleados
fichan su entrada, pausas y salida; los administradores gestionan empleados,
revisan y aprueban correcciones, y consultan informes y auditoría.

## Alcance del MVP

- Registro de organización (tenant) y alta de usuarios (`TENANT_ADMIN`,
  `EMPLOYEE`).
- Autenticación JWT con refresh token rotatorio.
- Fichaje de jornada con pausas (Workday + BreakEntry).
- Solicitud y resolución de correcciones sobre jornadas ya registradas.
- Auditoría de operaciones sensibles.
- Informes básicos de horas trabajadas.
- Eventos de integración vía Transactional Outbox (sin broker).

## Fuera de alcance

Microservicios, mensajería con broker (Kafka/RabbitMQ), CQRS completo, event
sourcing, nóminas, planificación de turnos, integraciones con terceros.

## Principios

- **Monolito modular**: módulos con límites claros dentro de un único
  desplegable.
- **Clean Architecture** por módulo: `domain` no depende de frameworks.
- **Multitenancy por columna** `tenant_id`, resuelta siempre del usuario
  autenticado.
- **Seguridad por defecto**: validación de entradas, autorización por rol y
  tenant, sin secretos en el repositorio.
- **Trazabilidad**: decisiones registradas como ADR, documentación viva junto
  al código.
