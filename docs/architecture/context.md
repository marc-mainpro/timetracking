# Contexto (C4 — Nivel 1)

## Actores

- **Empleado**: ficha su jornada (entrada, pausas, salida), consulta su
  historial y solicita correcciones.
- **Administrador de tenant (`TENANT_ADMIN`)**: gestiona empleados, revisa y
  resuelve correcciones, consulta informes y auditoría.

## Sistema

**Sistema de Control Horario** (este MVP): SaaS multitenant que expone una
API REST (backend Spring Boot) consumida por una SPA Angular. Persiste en
PostgreSQL y publica eventos de integración a través de una tabla Outbox.

## Sistemas externos (futuro, fuera del MVP)

- **Consumidores de eventos de integración**: sistemas externos (nóminas,
  analítica) que en el futuro podrán leer la tabla `outbox_message` o un
  relay hacia un broker. En el MVP no existe ningún consumidor real.

## Diagrama (texto)

```text
[Empleado] --HTTPS--> [SPA Angular] --HTTPS/JWT--> [API Control Horario] --JDBC--> [PostgreSQL]
[Administrador] --HTTPS--> [SPA Angular]
[API Control Horario] --outbox (futuro)--> [Consumidores externos]
```
