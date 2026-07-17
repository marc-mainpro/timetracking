## T302 — Repositorios tenant-aware

### Cambios

- Se aplicó la convención explícita tenant-aware en `UserRepository` añadiendo `findById(UUID tenantId, UUID id)` como vía de negocio por defecto.
- El adaptador JPA y su repositorio interno pasaron a soportar `findByTenantIdAndId`.
- `IdentityAuthenticatedPrincipalStateChecker` ahora verifica el principal con consulta tenant-aware, no con recuperación global seguida de comparación manual.
- Se añadió `findAllByTenantId(UUID tenantId)` como soporte natural para casos administrativos y para la suite cross-tenant de T303.
- Se creó `RepositoryTenantConventionTest` para verificar por reflexión que los métodos de `UserRepository` siguen la convención o están listados como excepción documentada.
- Se documentó la convención tenant-aware y sus excepciones justificadas en arquitectura y en la skill de revisión multitenant.

### Pruebas (comandos ejecutados y resultado)

```text
cd backend && mvn -B verify
```

Resultado: **BUILD SUCCESS**. `Tests run: 105, Failures: 0, Errors: 0, Skipped: 0`.

Casos cubiertos específicamente por T302:

- `findById(tenantId, id)` devuelve vacío con tenant incorrecto aunque el id exista.
- La convención de repositorio tenant-aware queda verificada por test dedicado.

### Cobertura

- JaCoCo se mantiene en verde para los umbrales de `domain` y `application`.
- La nueva ruta tenant-aware en `UserRepository` y su adaptador quedan cubiertas por integración real.

### Seguridad

- La consulta principal de negocio sobre `User` deja de depender de disciplina manual y pasa a forzar `tenantId` en la firma del puerto.
- Las excepciones tenant-unaware restantes quedan acotadas a autenticación/registro documentados (`findByEmail`, `existsByEmail`, `findById(id)` para refresh/login globales existentes).

### Documentación actualizada

- `docs/architecture/components.md`
- `.skills/review-multitenancy/SKILL.md`

### ADR

- No se añadió ADR nueva: la convención explícita tenant-aware desarrolla decisiones ya fijadas en SDD/contexto sobre no confiar en tenant del cliente y filtrar toda query de negocio por tenant.

### Riesgos detectados

1. Persiste una excepción documentada para flujos de auth/refresh que todavía no disponen de `TenantContext` previo (`UserRepository.findById(id)` y métodos globales por email). No afecta a endpoints de negocio actuales, pero conviene revisarlo cuando haya más casos administrativos y repositorios tenant-aware.
2. `RefreshTokenRepository` sigue sin columna `tenant_id`; por ahora queda fuera de la convención explícita al pertenecer a un flujo de sesión aún no tenant-scoped por firma.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T302.
