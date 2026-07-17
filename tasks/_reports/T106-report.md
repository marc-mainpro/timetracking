## T106 — Reglas ArchUnit

### Cambios
Creado el paquete `backend/src/test/java/com/tfp/timetracking/architecture/` con 6 clases de test ArchUnit (JUnit 5, `@AnalyzeClasses(packages = "com.tfp.timetracking", importOptions = ImportOption.DoNotIncludeTests.class)`), una por cada regla de la ficha:

1. **`DomainPurityTest`** (regla 1): 5 `@ArchTest` — ningún `..domain..` depende de `org.springframework..`, `jakarta.persistence..`, `..application..`, `..infrastructure..` ni `..interfaces..`.
2. **`RestLayerAccessTest`** (regla 2): `..interfaces.rest..` no depende de `..infrastructure.persistence..` ni de interfaces `*Repository` residentes en `..domain..` (predicado combinado `resideInAPackage("..domain..").and(simpleNameEndingWith("Repository"))`).
3. **`LayeredArchitectureTest`** (regla 3): `layeredArchitecture()` con capas `Interfaces` → `Application` → `Domain` y `Infrastructure`; `Interfaces` no accedida por nadie, `Application` solo por `Interfaces`/`Infrastructure`, `Domain` solo por `Application`/`Infrastructure`, `Infrastructure` no accedida por nadie (el cableado de Spring vía anotaciones/component-scan no genera un import Java y por tanto no cuenta como dependencia aquí; queda documentado en el Javadoc de la clase).
4. **`ModuleCyclesTest`** (regla 4): `slices().matching("com.tfp.timetracking.(*)..").should().beFreeOfCycles()` sobre los 8 módulos.
5. **`OutboxEncapsulationTest`** (regla 5): `..outbox.infrastructure..` solo puede ser accedida desde paquetes `..outbox..` (ningún otro módulo la usa directamente).
6. **`DomainEventImmutabilityTest`** (regla 6): todos los campos de clases en `..domain.event..` son `final` (cubre clases clásicas y `record`, cuyos componentes ya son campos privados finales); y `..domain.event..` no depende de Spring ni de JPA (redundante con `DomainPurityTest` pero explícito por lo que pide la ficha).

Todas las reglas usan `allowEmptyShould(true)` (o su equivalente en `layeredArchitecture()`) para pasar en verde sobre el esqueleto actual, donde la mayoría de paquetes `domain`/`application`/`infrastructure`/`interfaces.rest` solo contienen `package-info.java` y no hay clases `..domain.event..` ni `..outbox.infrastructure..` todavía.

Se usó `ImportOption.DoNotIncludeTests` para que el análisis de arquitectura solo considere las clases de `src/main/java` (excluye `target/test-classes`, incluidas las propias clases de test de arquitectura y `ApplicationSmokeTest`).

No se ha tocado ningún fichero de `src/main/java` ni de `pom.xml`: solo se añadieron los 6 ficheros de test indicados arriba y este informe, conforme al alcance de la tarea.

### Pruebas (comandos ejecutados y resultado)
```
cd backend && mvn -B test
```
Resultado: **BUILD SUCCESS**. `Tests run: 14` en el paquete `architecture` (LayeredArchitectureTest: 1, ModuleCyclesTest: 1, OutboxEncapsulationTest: 1, DomainEventImmutabilityTest: 3, DomainPurityTest: 5, RestLayerAccessTest: 2) + 1 del smoke test existente, todos en verde.

```
cd backend && mvn -B verify
```
Resultado: **BUILD SUCCESS**. Los 14 tests de arquitectura y el smoke test pasan; JaCoCo `check` (dominio ≥90 %, aplicación ≥80 %) "All coverage checks have been met." (paquetes vacíos salvo `shared`, igual que en T101).

**Evidencia de que las reglas detectan violaciones reales** (se introdujeron dos violaciones temporales, se confirmó el fallo, y se revirtieron antes de terminar — no queda ningún rastro en el árbol de trabajo):

1. Violación de pureza de dominio: se creó temporalmente `backend/src/main/java/com/tfp/timetracking/shared/domain/ViolationTemp.java`, una clase anotada con `@org.springframework.stereotype.Component`. Al ejecutar `mvn -B test -Dtest=DomainPurityTest`, el test `domain_should_not_depend_on_spring` **falló** con:
   ```
   Architecture Violation [Priority: MEDIUM] - Rule 'no classes that reside in a package
   '..domain..' should depend on classes that reside in any package
   ['org.springframework..']' was violated (1 times):
   Class <com.tfp.timetracking.shared.domain.ViolationTemp> is annotated with
   <org.springframework.stereotype.Component> in (ViolationTemp.java:0)
   ```
   Fichero eliminado inmediatamente después de confirmar el fallo.

2. Violación de capas: se creó temporalmente `backend/src/main/java/com/tfp/timetracking/shared/domain/LayerViolationTemp.java`, con un campo de tipo `com.tfp.timetracking.shared.infrastructure.SystemClock` (dominio dependiendo de infraestructura). Al ejecutar `mvn -B test -Dtest=LayeredArchitectureTest`, el test `layers_are_respected` **falló** con:
   ```
   Architecture Violation [Priority: MEDIUM] - Rule 'Layered architecture ... where layer
   'Infrastructure' may not be accessed by any layer' was violated (1 times):
   Field <com.tfp.timetracking.shared.domain.LayerViolationTemp.clock> has type
   <com.tfp.timetracking.shared.infrastructure.SystemClock> in (LayerViolationTemp.java:0)
   ```
   Fichero eliminado inmediatamente después de confirmar el fallo.

Tras revertir ambas violaciones, se volvió a ejecutar `mvn -B verify` confirmando **BUILD SUCCESS** de nuevo (ver arriba), y `git status` en `backend/` no muestra ningún fichero temporal ni cambio fuera de los 6 tests de arquitectura añadidos.

### Cobertura
Sin cambios respecto a T101/T105: los tests de arquitectura no son código de producción y no computan en los umbrales JaCoCo de `domain`/`application`. `mvn verify` confirma "All coverage checks have been met." para ambas reglas.

### Seguridad
No aplica (tarea de solo tests de arquitectura, sin tocar dependencias, endpoints ni configuración de seguridad).

### Documentación actualizada
Ninguna fuera de este informe: la ficha no pide tocar `docs/`, OpenAPI ni catálogo de eventos.

### ADR
No aplica: T106 no toma ninguna decisión nueva fuera de lo ya fijado en CONTEXT-GLOBAL §4; solo verifica en ArchUnit las reglas de Clean Architecture ya decididas.

### Riesgos detectados
- La regla de capas (`LayeredArchitectureTest`) no puede detectar el caso "una clase de configuración de infraestructura es instanciada solo por el contenedor de Spring vía reflexión/component-scan" porque eso no genera un import Java visible para ArchUnit; esto es intencional (así es como Spring cablea `@Configuration`/`@Component`) pero conviene que quede documentado (lo está, en el Javadoc de `LayeredArchitectureTest`) para que nadie interprete la ausencia de violación como garantía de que ninguna clase de aplicación usa una clase de infraestructura por inyección de una interfaz que resida en infraestructura (no debería ocurrir si los puertos siguen definiéndose en `domain`/`application`, tal y como exige CONTEXT-GLOBAL §4 "infrastructure implementa puertos definidos en domain/application").
- Mientras casi todos los módulos sigan teniendo solo `package-info.java`, todas estas reglas están en modo "verde por definición" (`allowEmptyShould(true)`/capas sin clases relevantes); empezarán a exigir de verdad en cuanto se añada código real en tareas futuras. Esto es el comportamiento esperado y explícito en "Fuera de alcance" de la ficha, pero se deja constancia por si se repite el patrón de falso verde temprano ya señalado en el riesgo equivalente de T101 para JaCoCo.

### Pendientes / decisiones que necesitan humano
Ninguna decisión bloqueante. Un matiz de interpretación, no fijado literalmente en la ficha, por si el humano quiere confirmarlo:
1. La ficha dice "interfaces.rest no accede... a interfaces *Repository de dominio directamente (solo a casos de uso de application)"; se implementó restringiendo el acceso a clases con sufijo `Repository` que residan en `..domain..` (cualquier submódulo), en vez de limitarlo a un paquete `..domain.repository..` concreto (que no existe como convención fijada en CONTEXT-GLOBAL). Si en el futuro se decide un paquete/sufijo distinto para los puertos de repositorio, la regla se ajustará en consecuencia.
