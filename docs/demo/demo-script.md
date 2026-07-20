# Guion de demo

Duración estimada: 10 minutos.

## Preparación

1. `cp .env.example .env`
2. `docker compose up -d --build`
3. `./scripts/seed-demo.sh`
4. Abrir frontend en `http://localhost:4200` y Swagger en `http://localhost:8080/swagger-ui.html`

## Flujo

1. Mostrar que existen dos tenants de demo distintos.
2. Entrar como empleado A y completar jornada con pausa.
3. Intentar una transición inválida para enseñar el rechazo de negocio.
4. Solicitar una corrección sobre la jornada cerrada.
5. Entrar como admin A y aprobar la corrección.
6. Consultar auditoría de la aprobación en `/api/v1/admin/audit-events`.
7. Abrir informes admin y descargar CSV.
8. Mostrar que el tenant B no ve datos del tenant A.
9. Consultar `/actuator/metrics/outbox.messages.published` para evidenciar
   publicación de outbox.

## Credenciales sugeridas

- Admin A: `admin-a@acme.test` / `supersecretpwd`
- Employee A: `employee-a@acme.test` / `employeepwd123`
- Admin B: `admin-b@acme.test` / `supersecretpwd`
- Employee B: `employee-b@acme.test` / `employeepwd123`
