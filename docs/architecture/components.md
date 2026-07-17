# Componentes (C4 — Nivel 3, backend)

Paquete base: `com.tfp.timetracking`. Módulos (paquetes de primer nivel):

- `identity`: usuarios, autenticación, roles.
- `tenant`: organizaciones (tenants).
- `timetracking`: jornadas (Workday) y pausas (BreakEntry).
- `corrections`: solicitudes de corrección sobre jornadas.
- `reporting`: informes de horas trabajadas.
- `audit`: registro de auditoría de operaciones sensibles.
- `outbox`: mensajes de integración transaccionales.
- `shared`: utilidades comunes (errores, tipos de dominio compartidos).

En `shared.application` residen además puertos transversales usados por varios
módulos, como `TenantContext` (tenant, usuario y roles resueltos desde el
principal autenticado) y `AuthenticatedPrincipalStateChecker`.

Cada módulo se organiza en capas, de dentro hacia fuera:

```text
<módulo>/
├── domain/            # entidades, invariantes, eventos de dominio, puertos
├── application/        # casos de uso, orquestación de dominio
├── infrastructure/      # adaptadores: JPA, seguridad, outbox, clock, etc.
└── interfaces.rest/     # controladores REST, DTO de API
```

## Reglas de dependencia (verificadas por ArchUnit)

- `domain` no importa Spring, JPA ni `application`/`infrastructure`/`interfaces`.
- Los controladores no contienen lógica de negocio ni acceden a
  repositorios directamente; delegan en casos de uso de `application`.
- `infrastructure` implementa puertos definidos en `domain`/`application`.
- El tenant operativo se resuelve desde el principal autenticado, nunca desde
  DTOs o parámetros del cliente.
- Sin ciclos de dependencia entre módulos.
- Entidades JPA separadas del modelo de dominio y de los DTO de API.
