# ADR-0003: Maven, paquete base y estructura de módulos

* Estado: accepted
* Fecha: 2026-07-17

## Contexto y problema

Se necesita fijar el sistema de build del backend y la organización de
paquetes para que sea consistente en todas las iteraciones y verificable con
ArchUnit.

## Decisión

* Build: **Maven**, proyecto único `backend/`.
* Paquete base: `com.tfp.timetracking`.
* Módulos (paquetes de primer nivel bajo el paquete base): `identity`,
  `tenant`, `timetracking`, `corrections`, `reporting`, `audit`, `outbox`,
  `shared`.
* Capas por módulo: `domain` / `application` / `infrastructure` /
  `interfaces.rest`.

## Consecuencias

* (+) Estructura predecible y uniforme entre módulos, facilita la
  verificación automática con ArchUnit (`docs/architecture/components.md`).
* (+) Un único `pom.xml` simplifica el build y el CI.
* (-) Todo el backend comparte un mismo ciclo de vida de build; no se puede
  versionar o desplegar un módulo de forma independiente (aceptado, ver
  ADR-0001).
