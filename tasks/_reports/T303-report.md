## T303 — Suite de pruebas cross-tenant

### Cambios

- Se añadió `TestTenantFactory` reutilizable para crear dos tenants con admin y empleado, usando:
  - registro real vía endpoint
  - creación de empleado vía dominio/repositorio
  - login real para obtener tokens de ambos actores
- Se añadió `CrossTenantSecurityIntegrationTest` con controladores de prueba protegidos por rol para demostrar aislamiento cross-tenant reutilizable y ampliable.
- La suite cubre:
  - admin de A no lista ni obtiene por id usuarios de B
  - mutación sobre recurso de B -> `404`
  - JWT forjado con `sub` de A y `tenantId` de B -> `401 INVALID_CREDENTIALS`
  - desactivar tenant B no afecta a A y B queda bloqueado
  - empleado -> `403` en endpoint admin-only
- Se dejó documentada en la skill de multitenancy la obligación de ampliar esta suite en toda tarea futura con endpoints nuevos.

### Pruebas (comandos ejecutados y resultado)

```text
cd backend && mvn -B verify
```

Resultado: **BUILD SUCCESS**. `Tests run: 105, Failures: 0, Errors: 0, Skipped: 0`.

### Cobertura

- La suite cross-tenant se apoya en Testcontainers + MockMvc + JWT real y queda incluida en la verificación global.
- JaCoCo permanece en verde para los umbrales configurados.

### Seguridad

- El aislamiento entre tenants queda demostrado con respuestas `404` al acceder por id a recursos ajenos, evitando revelar existencia.
- El principal autenticado no puede cambiar de tenant manipulando claims sin ser rechazado.
- La autorización por rol queda verificada con `403` para `EMPLOYEE` en endpoints administrativos.

### Documentación actualizada

- `.skills/review-multitenancy/SKILL.md`

### ADR

- No se añadió ADR nueva.
- La antigua expectativa de “mismo email en A y B” ya no aplica por `ADR-0008` (email globalmente único para autenticación), y queda sustituida por pruebas de JWT forjado / claims cruzados.

### Riesgos detectados

1. La suite actual usa endpoints de prueba protegidos por rol como base reusable hasta que existan más endpoints de negocio; deberá ampliarse en T403, T501, T602, T801, etc.
2. El caso de “mismo email en A y B” del enunciado original quedó obsoleto por la decisión posterior `ADR-0008`; la suite compensa ese hueco con casos de claims cruzados y acceso por recurso.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T303.
- La siguiente tarea correcta es `T401`.
