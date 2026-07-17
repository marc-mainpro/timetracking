# Skill: create-rest-endpoint

## Objetivo

Exponer un caso de uso de `application` como endpoint REST en
`interfaces.rest`, sin lógica de negocio en el controlador y con el formato
de error Problem Details del proyecto.

## Entradas

- Caso de uso ya implementado en `application` (ver skill
  `create-use-case`).
- Contrato de entrada/salida (DTO de request/response).
- Rol(es) autorizados (`TENANT_ADMIN`, `EMPLOYEE`).

## Pasos

1. Crear DTO de request/response en `<módulo>/interfaces.rest`, separados de
   las entidades JPA y del modelo de dominio.
2. Anotar el DTO de request con Bean Validation (`@NotNull`, `@Email`, etc.)
   según las reglas del agregado.
3. Crear el controlador (o método) que:
   - solo mapea DTO → comando del caso de uso → resultado del caso de uso →
     DTO de respuesta;
   - no accede a repositorios ni contiene condicionales de negocio;
   - restringe el acceso por rol con Spring Security
     (`@PreAuthorize` o configuración equivalente).
4. Verificar que ningún parámetro de entrada permite fijar `tenantId`; el
   tenant se resuelve del principal autenticado.
5. Registrar el endpoint en la configuración de springdoc-openapi (anotado o
   detectado automáticamente) para que se genere en el OpenAPI.
6. Añadir el manejo de excepciones de dominio al `@ControllerAdvice` común
   si el `errorCode` es nuevo (mapeo `DomainException` → HTTP status +
   Problem Details).

## Validaciones

- El controlador no contiene lógica de negocio ni accede a repositorios
  directamente.
- Toda entrada del DTO tiene validación Bean Validation.
- CORS y cabeceras de seguridad ya configuradas se mantienen intactas.
- Las respuestas de error siguen el formato Problem Details de
  `docs/adr/ADR-0006-problem-details-errores.md`.

## Pruebas

- Test de integración del controlador (`@SpringBootTest` o `MockMvc` con
  Testcontainers) cubriendo: caso feliz, validación fallida (400),
  conflicto de negocio (409), acceso no autorizado (401/403), acceso
  cross-tenant (403/404 según corresponda).
- Test de contrato del DTO (serialización/deserialización) si el DTO es
  complejo.

## Criterios de finalización

- `mvn verify` en verde.
- OpenAPI actualizado y coherente con el endpoint real.
- Ningún dato de otro tenant es accesible desde el endpoint.

## Archivos que puede modificar

- `backend/src/main/java/com/tfp/timetracking/<módulo>/interfaces/rest/**`
- `backend/src/test/java/com/tfp/timetracking/<módulo>/interfaces/rest/**`
- Configuración de springdoc-openapi si aplica.

## Archivos que debe actualizar

- `docs/api/README.md` (o el export OpenAPI referenciado) tras el cambio.
- `docs/domain/reglas-de-negocio.md` si el endpoint introduce un nuevo
  `errorCode`.
- `tasks/_reports/TXXX-report.md` de la tarea en curso.
