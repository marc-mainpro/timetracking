# ADR-0007: Excepción puntual de ArchUnit para el traductor global de errores

* Estado: accepted
* Fecha: 2026-07-17

## Contexto y problema

CONTEXT-GLOBAL §4 exige capas `interfaces -> application -> domain` sin que
`interfaces` dependa directamente de `domain` (verificado por
`LayeredArchitectureTest`: `domain` solo puede ser accedido por
`application`/`infrastructure`). Al mismo tiempo, CONTEXT-GLOBAL §7 y
ADR-0006 exigen un `@RestControllerAdvice` único (`GlobalExceptionHandler`,
en `shared.interfaces.rest`) que traduzca `DomainException` (en
`shared.domain`) a Problem Details, leyendo su `errorCode` y su mensaje.

Cualquier `@ExceptionHandler(DomainException.class)` necesita el tipo
`DomainException` en su firma, lo que crea una dependencia real
`interfaces.rest -> shared.domain` y rompe la regla de capas tal cual estaba
formulada (T203 fue la primera tarea en ejercer este camino: hasta ahora
`interfaces.rest` solo contenía `package-info.java`).

## Alternativas consideradas

1. **Relajar la regla en general** (permitir que cualquier clase de
   `interfaces` dependa de `domain`): descartada, diluye la regla para todos
   los módulos futuros, no solo para el manejador de errores.
2. **Mover `DomainException` fuera de `..domain..`** (p. ej. a
   `shared.errors`): descartada, `DomainException` es dominio puro (sin
   Spring/JPA) y forma parte del modelo de dominio (la lanzan los agregados);
   sacarla de `domain` sería cosmético y confuso.
3. **Excepción puntual y explícita en ArchUnit** (elegida): usar
   `ignoreDependency(GlobalExceptionHandler.class, DomainException.class)`
   en `LayeredArchitectureTest`, documentada con un comentario que explica
   por qué es la única dependencia permitida de `interfaces` hacia `domain`.

## Decisión

Se añade una única excepción explícita y nominal (no un patrón amplio) a
`LayeredArchitectureTest`: `GlobalExceptionHandler` puede depender de
`DomainException` para leer `errorCode()`/`getMessage()` al traducir a
Problem Details. Ninguna otra clase de `interfaces` puede depender de
`domain`; cualquier nueva excepción de dominio sigue extendiendo
`DomainException` y no necesita una excepción de ArchUnit adicional (la regla
cubre el tipo base, no cada subtipo).

## Consecuencias

* (+) La regla de capas sigue siendo estricta para el resto del código; la
  única grieta es nominal, revisable en cada PR que la toque.
* (+) `DomainException` permanece en `domain`, dominio puro, sin Spring.
* (-) Si en el futuro se necesita otra dependencia legítima
  `interfaces -> domain` (poco probable dado el patrón anterior), habrá que
  añadir otra excepción nominal o reconsiderar esta ADR.
