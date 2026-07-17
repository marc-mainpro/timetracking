# ADR-0001: Monolito modular

* Estado: accepted
* Fecha: 2026-07-17

## Contexto y problema

El MVP debe soportar control horario multitenant con módulos claramente
separados (identidad, tenants, fichajes, correcciones, informes, auditoría,
outbox), pero el equipo y el alcance temporal no justifican la complejidad
operativa de microservicios.

## Decisión

Construir un **monolito modular**: un único desplegable (`backend/`) dividido
en paquetes por módulo (`identity`, `tenant`, `timetracking`, `corrections`,
`reporting`, `audit`, `outbox`, `shared`), cada uno con capas
`domain`/`application`/`infrastructure`/`interfaces.rest`. Prohibido
introducir microservicios, Kafka/RabbitMQ, CQRS completo o event sourcing sin
un nuevo ADR que lo justifique.

## Consecuencias

* (+) Despliegue y operación simples; transacciones locales sencillas
  (crítico para el patrón Outbox).
* (+) Límites de módulo verificables con ArchUnit sin la complejidad de red
  distribuida.
* (-) Escalado independiente por módulo no es posible sin refactor futuro.
* (-) Requiere disciplina para no degenerar en un monolito no modular
  ("big ball of mud"); se mitiga con ArchUnit y revisión de PRs.
