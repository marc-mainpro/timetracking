# Skill: create-use-case

## Objetivo

Crear o modificar un caso de uso de `application` que orqueste un agregado de
`domain` (Tenant, User, Workday, CorrectionRequest, ...) respetando Clean
Architecture, multitenancy y el formato de error del proyecto.

## Entradas

- Módulo destino (`identity`, `tenant`, `timetracking`, `corrections`,
  `reporting`, `audit`, `outbox`).
- Regla(s) de negocio a implementar (ver `docs/domain/reglas-de-negocio.md`).
- Puertos de `domain`/`application` necesarios (repositorios, reloj,
  publicador de eventos, etc.).

## Pasos

1. Ubicar o crear el agregado/entidad de dominio en `<módulo>/domain`, con
   sus invariantes como métodos del agregado (nunca en el caso de uso).
2. Definir el/los puerto(s) necesarios como interfaces en `domain` o
   `application` (p. ej. `WorkdayRepository`).
3. Implementar el caso de uso en `<módulo>/application` como una clase que:
   - recibe el `tenantId` SIEMPRE desde `TenantContext`, nunca de un DTO de
     entrada;
   - carga el agregado vía el puerto, invoca el método de dominio
     correspondiente, persiste y recoge los eventos de dominio generados;
   - traduce excepciones de dominio (`DomainException`) sin capturarlas para
     ocultarlas: deben propagarse para que la capa `interfaces.rest` las
     traduzca a Problem Details.
4. No añadir dependencias de Spring/JPA en el propio caso de uso más allá de
   `@Transactional`/inyección de dependencias si el módulo ya lo usa así;
   nunca en `domain`.
5. Si el caso de uso publica eventos de integración, delegar en el
   mecanismo de Outbox (ver skill `create-integration-event`), nunca
   publicar directamente a un broker.

## Validaciones

- El caso de uso no contiene lógica de negocio duplicada del agregado
  (single source of truth = `domain`).
- El `tenantId` nunca llega como parámetro público modificable por el
  cliente.
- Toda excepción de dominio tiene un `errorCode` ya catalogado en
  `docs/domain/reglas-de-negocio.md` (si no, añadirlo).
- ArchUnit sigue en verde tras el cambio.

## Pruebas

- Test unitario del agregado para cada invariante tocada (dominio).
- Test unitario del caso de uso con dobles de los puertos (aplicación).
- Si el caso de uso toca persistencia real, test de integración con
  Testcontainers PostgreSQL.
- Test cross-tenant si el caso de uso accede a datos de negocio.

## Criterios de finalización

- `mvn verify` en verde, cobertura de dominio ≥90 % y aplicación ≥80 %
  mantenida.
- ArchUnit en verde.
- Ninguna regla de negocio tocada sin test.

## Archivos que puede modificar

- `backend/src/main/java/com/tfp/timetracking/<módulo>/domain/**`
- `backend/src/main/java/com/tfp/timetracking/<módulo>/application/**`
- `backend/src/test/java/com/tfp/timetracking/<módulo>/**`

## Archivos que debe actualizar

- `docs/domain/reglas-de-negocio.md` si se añade o modifica una regla o un
  `errorCode`.
- `docs/testing/informe-cobertura.md` tras `mvn verify`.
- Un nuevo ADR en `docs/adr/` si toma una decisión no fijada en
  `tasks/_context/CONTEXT-GLOBAL.md`.
