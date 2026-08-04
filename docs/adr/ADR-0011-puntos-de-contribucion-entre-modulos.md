# ADR-0011: Puntos de contribución entre módulos en lugar de listas centrales

* Estado: accepted
* Fecha: 2026-08-04

## Contexto y problema

El monolito modular crece de 8 a 13 módulos durante la V2 (`calendar`, `absence`,
`shift`, `notification`, y ampliaciones de `identity` y `tenant`). Al planificar
ese crecimiento se detectó que cuatro ficheros obligaban a **todo módulo nuevo a
editar código propiedad de otro módulo** solo para darse de alta:

1. `LayeredArchitectureTest` enumeraba un `ignoreDependency(Mapper, TipoDominio)`
   por cada par. Un `*RestMapper` nuevo rompía el build hasta añadir sus líneas
   —y su import— a un test compartido por todos los módulos.
2. `OutboxDomainEventPublisher` (módulo `outbox`) encadenaba `.or(...)` con el
   mapper de integración de cada módulo de negocio.
3. `SecurityConfig` (módulo `shared`) mantenía la lista literal de rutas
   `permitAll()` de todos los módulos.
4. `application.yml` acumulaba un bloque de configuración por funcionalidad.

Esto es tolerable con desarrollo secuencial, pero la V2 se implementa con varios
agentes trabajando en paralelo sobre worktrees separados. Cada uno de esos cuatro
ficheros es un conflicto de merge garantizado, y el primero además rompe el build
del resto en cuanto alguien crea un mapper.

El problema de fondo no es el conflicto de merge: es que el diseño obligaba a un
módulo a **conocer y modificar** a otro para existir, justo lo que el monolito
modular pretende evitar.

## Decisión

1. **Regla por convención en ArchUnit.** `LayeredArchitectureTest` permite la
   dependencia `..interfaces.rest.. -> ..domain..` para toda clase cuyo nombre
   acabe en `RestMapper`. Traducir un agregado a su DTO exige leerlo, y ese es
   exactamente el trabajo del mapper de borde: es traducción, no lógica de
   negocio. Las excepciones de `*Controller` **siguen siendo explícitas una a
   una**: que un controlador conozca un tipo de dominio debe seguir doliendo.

2. **Puerto `IntegrationEventMapper` en `shared.application`.** Cada módulo
   aporta su implementación como `@Component`; `OutboxDomainEventPublisher` las
   recibe inyectadas como `List<IntegrationEventMapper>`. La interfaz vive en
   `shared` —no en `outbox`— para que la dependencia siga siendo
   `outbox -> módulo` y no se cierre el ciclo que prohíbe `ModuleCyclesTest`.

3. **Puerto `PublicEndpointsContributor` en `shared.infrastructure.security`.**
   Cada módulo declara sus rutas públicas. `shared` aporta salud y documentación,
   `identity` login y refresh, `tenant` el alta pública. **El orden de los filtros
   no es contribuible** y se mantiene centralizado: es una decisión global sobre
   la cadena, y abrirla a contribuciones haría que el comportamiento dependiese
   del orden de arranque de los beans.

4. **Configuración por fichero.** `application.yml` declara por adelantado los
   `spring.config.import` opcionales de `config/*.yml` para todas las
   funcionalidades previstas de la V2. Al ser `optional:`, un fichero inexistente
   se ignora, así que la lista se escribe una vez y nadie vuelve a tocarla. Spring
   Boot no admite comodines en imports `classpath:`, de ahí la enumeración.

## Consecuencias

* (+) Añadir un módulo es añadir ficheros, no editar los de otros módulos.
* (+) Desaparecen cuatro conflictos de merge sistemáticos entre agentes paralelos.
* (+) El acoplamiento queda expresado como puerto explícito, coherente con la
  arquitectura hexagonal que ya sigue el resto del proyecto.
* (−) Las rutas públicas dejan de verse en un único fichero. Se mitiga con
  `RouteAuthorizationIntegrationTest`, que recorre **todas** las rutas
  registradas y exige 401 salvo lista blanca explícita: un endpoint abierto por
  descuido rompe ese test.
* (−) La regla por convención de ArchUnit es menos precisa que la enumeración:
  un `*RestMapper` podría abusar del acceso al dominio sin que el test lo
  detecte. Se acepta porque `RestLayerAccessTest` sigue prohibiendo que la capa
  `interfaces` toque repositorios o persistencia, que es el abuso que importa.
* (−) La configuración queda repartida en varios ficheros; hay que mirar
  `config/` además de `application.yml`.

## Alternativas descartadas

* **Mantener las listas y coordinar los merges a mano**: traslada un problema de
  diseño a un problema de proceso, y hay que pagarlo en cada una de las cinco
  olas de la V2.
* **Relajar la regla de capas de ArchUnit para todo `interfaces -> domain`**:
  eliminaría también el control sobre los controladores, que es donde el acceso
  al dominio sí indica lógica de negocio fuera de sitio.
* **Mover los mappers de integración al módulo `outbox`**: concentraría el
  conocimiento de todos los dominios en un módulo de infraestructura y crearía
  exactamente el acoplamiento que ADR-0005 evita.
* **`spring.config.import` con comodín** (`optional:classpath:config/*.yml`):
  Spring Boot solo admite comodines en localizaciones `file:`, no `classpath:`.
