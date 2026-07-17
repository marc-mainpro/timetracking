# T106 — Reglas ArchUnit

- Iteración: 1 · Depende de: T101 · Contexto: CONTEXT-GLOBAL §4

## Objetivo
Tests ArchUnit que hagan cumplir la arquitectura desde el primer día.

## Detalle
Crear `ArchitectureTest` (o varios) en `backend/src/test/java` que verifiquen sobre `com.tfp.timetracking`:
1. Ningún `..domain..` depende de `org.springframework..`, `jakarta.persistence..` ni de `..application..`, `..infrastructure..`, `..interfaces..`.
2. `..interfaces.rest..` no accede a `..infrastructure.persistence..` ni a interfaces `*Repository` de dominio directamente (solo a casos de uso de `..application..`).
3. Capas: interfaces → application → domain; infrastructure puede depender de domain/application; nadie depende de infrastructure (salvo configuración).
4. Sin ciclos entre slices `com.tfp.timetracking.(*)..`.
5. Clases de `..outbox..` de infraestructura no son accedidas desde otros módulos salvo por sus puertos.
6. Eventos de dominio (`..domain.event..`): inmutables (campos finales o records) y sin dependencias de Spring/JPA.

## Fuera de alcance
Reglas sobre código aún inexistente que obliguen a crear clases: las reglas deben pasar en verde sobre el esqueleto actual (usar `allowEmptyShould(true)` donde aplique).

## Criterios de aceptación
- `mvn verify` verde; introducir a propósito una violación de prueba (y revertirla) confirma que la regla falla.

## Ficheros previstos
`backend/src/test/java/com/tfp/timetracking/architecture/*.java`.
