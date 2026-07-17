## T203 — Caso de uso RegisterTenant + endpoint

### Cambios

**`tenant/application`**
- `RegisterTenantCommand` (record): tenantName, timezone, adminEmail, adminPassword, adminFirstName, adminLastName.
- `RegisterTenantResult` (record): tenantId, adminUserId.
- `RegisterTenantUseCase` (`@Service`, método `register` `@Transactional`): crea `Tenant.register(...)`, valida email de admin no duplicado dentro del tenant (`UserRepository.existsByTenantIdAndEmail`, lanza `EmailAlreadyInUseException` si lo está), hashea el password vía el puerto `PasswordHasher`, crea el `User` admin (`Role.TENANT_ADMIN`), persiste ambos agregados en la misma transacción y entrega los eventos acumulados (`TenantRegistered`, `EmployeeCreated`) al puerto `DomainEventPublisher`.

**`identity/domain`**
- `PasswordHasher` (puerto).
- `EmailAlreadyInUseException extends DomainException` (`errorCode = EMAIL_ALREADY_IN_USE`, ya catalogado en `docs/domain/reglas-de-negocio.md`).

**`identity/infrastructure/security`** (paquete nuevo)
- `BCryptPasswordHasher implements PasswordHasher` (usa `BCryptPasswordEncoder` directamente).

**`shared/domain`**
- `DomainEventPublisher` (puerto, `publish(List<Object>)`).

**`shared/infrastructure`**
- `LoggingDomainEventPublisher`: implementación provisional (solo loguea); comentario explícito de que T702 la sustituirá por escritura en `outbox_message` en la misma transacción.
- `SecurityConfig`: se añade `permitAll()` para `/api/v1/auth/register` (resto sigue `denyAll()`).

**`tenant/interfaces/rest`**
- `RegisterTenantRequest` (DTO, Bean Validation: `@NotBlank` en todos los campos, `@Email` en `adminEmail`, `@Size(min=10)` en `adminPassword`).
- `RegisterTenantResponse` (DTO: `tenantId`, `adminUserId`, sin datos sensibles).
- `AuthRegisterController`: `POST /api/v1/auth/register`, público, 201 + `Location`, sin lógica de negocio (solo mapea DTO ↔ comando/resultado).

**`shared/interfaces/rest`**
- `GlobalExceptionHandler` (`@RestControllerAdvice`, Problem Details RFC 7807 / CONTEXT-GLOBAL §7):
  - `DomainException` → 409, `errorCode` = `ex.errorCode()`.
  - `IllegalArgumentException` (invariantes de construcción del dominio, p. ej. timezone IANA inválida) → 400, `errorCode = INVALID_ARGUMENT`.
  - `MethodArgumentNotValidException` (Bean Validation de DTO) → 400, `errorCode = VALIDATION_ERROR`, con `errors: [{field, message}]`.
  - Todas las respuestas incluyen `correlationId` (UUID generado por respuesta) y `timestamp`.

**Tests**
- `tenant/application/RegisterTenantUseCaseTest` (Mockito): éxito (persiste tenant+admin, publica 2 eventos), timezone inválida (propaga `IllegalArgumentException` sin tocar repositorios), email de admin duplicado en el tenant (`EmailAlreadyInUseException`, `errorCode` verificado, sin tocar repositorios).
- `tenant/application/RegisterTenantUseCaseAtomicityIntegrationTest` (Testcontainers PostgreSQL, `TenantRepository` real + `UserRepository` doble que lanza excepción en `save`): confirma que no queda un tenant huérfano si falla la persistencia del admin (rollback transaccional).
- `tenant/interfaces/rest/AuthRegisterControllerIntegrationTest` (Testcontainers + MockMvc): 201 feliz (verifica filas reales en `tenant`, `app_user`, `user_role`), 400 de validación con detalle por campo (4 errores esperados), 400 por timezone inválida (`errorCode = INVALID_ARGUMENT`).
- `shared/domain/DomainExceptionTest`: cubre los dos constructores de `DomainException` y `errorCode()` para que el gate real de JaCoCo sobre `shared.domain` quede en verde.

**Arquitectura (`backend/src/test/.../architecture/LayeredArchitectureTest.java`)**
- Se añadió una excepción puntual y nominal: `ignoreDependency(GlobalExceptionHandler.class, DomainException.class)`. Sin ella, la regla de capas falla porque el `@RestControllerAdvice` necesita el tipo `DomainException` en su firma para leer `errorCode()`/mensaje. Documentado en el propio test y en ADR-0007 (ver más abajo). Ninguna otra regla ArchUnit se modificó.

### Pruebas (comandos ejecutados y resultado)

```
cd backend && mvn -B verify
```
Resultado: **BUILD SUCCESS**. `Tests run: 65, Failures: 0, Errors: 0, Skipped: 0` (56 previos + 9 nuevos: 3 unitarios del caso de uso, 3 de integración del controlador, 1 de atomicidad y 2 unitarios de `DomainException`). Las 14 reglas ArchUnit en verde (incluida la excepción documentada de `LayeredArchitectureTest`).

### Cobertura

JaCoCo (`backend/target/site/jacoco/jacoco.xml`) tras `mvn verify`:

| Paquete | LINE |
|---|---|
| `tenant.domain` | 49/49 = 100 % |
| `tenant.domain.event` | 1/1 = 100 % |
| `identity.domain` | 115/117 = 98.29 % |
| `identity.domain.event` | 4/4 = 100 % |
| `tenant.application` | 31/31 = 100 % |
| `shared.domain` | 7/7 = 100 % |

Umbrales del checklist (dominio ≥90 %, aplicación ≥80 %) ampliamente cumplidos por el código añadido. Detalle en `docs/testing/informe-cobertura.md`.

### Seguridad

- `POST /api/v1/auth/register` es el único endpoint nuevo con `permitAll()`; el resto de rutas siguen `denyAll()` (sin regresión).
- Password nunca se loguea ni se devuelve en la respuesta (`RegisterTenantResponse` solo expone ids).
- `adminPassword` se hashea con BCrypt antes de persistir (`password_hash` en BD); no se persiste en claro en ningún punto.
- `GlobalExceptionHandler` no expone stack traces ni mensajes de excepciones de infraestructura (solo mensajes de `DomainException`/`IllegalArgumentException`/Bean Validation, todos controlados por el propio dominio/DTO).
- Multitenancy: la comprobación de email duplicado se acota siempre por `tenantId` (`existsByTenantIdAndEmail`); dos tenants distintos pueden compartir email (test cubierto ya en T202, no modificado).

### Documentación actualizada

- `docs/api/README.md`: nueva sección "Endpoints implementados" con `POST /api/v1/auth/register`.
- `docs/testing/informe-cobertura.md`: cifras reales de cobertura tras T203 y un riesgo detectado (ver abajo) sobre el patrón de inclusión de JaCoCo.
- `docs/domain/reglas-de-negocio.md`: no requirió cambios (`EMAIL_ALREADY_IN_USE` ya estaba catalogado).

### ADR

- **ADR-0007** (nuevo): documenta la excepción puntual de ArchUnit que permite a `GlobalExceptionHandler` depender de `DomainException` para la traducción a Problem Details, con las alternativas descartadas y el razonamiento.

### Riesgos detectados

1. **Patrón de inclusión de JaCoCo poco efectivo** (`backend/pom.xml`, reglas `check-domain-coverage`/`check-application-coverage`): `<include>*.domain.*</include>` y `<include>*.application.*</include>` sólo casan con subpaquetes (p. ej. `identity.domain.event`), no con los paquetes "planos" `tenant.domain`, `identity.domain`, `tenant.application` donde vive el grueso del código. JaCoCo no falla si un patrón no casa con ningún paquete (regla vacía = "cumplida"), por lo que el `check` de Maven no está evaluando de forma efectiva el 90 %/80 % contra la mayoría del código real desde T202/T203. Medido manualmente, la cobertura real es ≥98 % en todos los paquetes afectados, así que no hay impacto práctico en T203, pero el gate automático no lo garantiza para tareas futuras. Recomendación: corregir los patrones (p. ej. `*.domain..*` con doble `*`, o exclusiones explícitas) en una tarea de infraestructura de build. No se corrigió en esta tarea por quedar fuera de los ficheros previstos en la ficha T203.
2. El `Location` de la respuesta 201 apunta a `/api/v1/tenants/{tenantId}`, un recurso que todavía no existe (no hay `GET /api/v1/tenants/{id}` implementado). Es una convención razonable (CONTEXT-API §3: "201 + Location en creaciones") pero no resuelve hasta que se implemente ese endpoint en una tarea futura.

### Pendientes / decisiones que necesitan humano

- Ninguna decisión bloqueante. El riesgo #1 (patrones JaCoCo) y el riesgo #2 (Location sin recurso) quedan anotados para una tarea futura; no se consideró en alcance de T203 (fuera de "Ficheros previstos" de la ficha).
