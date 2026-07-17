# Contenedores (C4 — Nivel 2)

| Contenedor | Tecnología | Responsabilidad |
|---|---|---|
| SPA Frontend | Angular (standalone components), TypeScript | UI para empleados y administradores; guarda el access token en memoria; nunca persiste el refresh token en `localStorage`. |
| API Backend | Java 21, Spring Boot 3.x, Spring Security | Expone la API REST; aplica autenticación/autorización, reglas de negocio y multitenancy. |
| Base de datos | PostgreSQL | Persistencia relacional; una única base de datos con `tenant_id` en toda tabla de negocio; migraciones con Flyway. |
| Outbox | Tabla `outbox_message` en PostgreSQL + publicador interno (polling) | Registra eventos de integración en la misma transacción que el cambio de negocio; no hay broker en el MVP. |

## Despliegue

Docker Compose orquesta: contenedor de backend, contenedor de frontend (o
build servido estáticamente) y contenedor de PostgreSQL. CI (GitHub Actions)
construye y prueba backend y frontend en cada cambio.
