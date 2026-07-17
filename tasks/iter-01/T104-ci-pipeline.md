# T104 — Pipeline de CI

- Iteración: 1 · Depende de: T101, T102, T103 · Contexto: CONTEXT-GLOBAL

## Objetivo
CI en GitHub Actions que valide backend y frontend en cada push/PR.

## Detalle
1. `.github/workflows/ci.yml` con jobs:
   - **backend**: JDK 21, cache Maven, `mvn -B verify` (Testcontainers funciona en runners GitHub), publicar informe JaCoCo como artefacto y fallar si no cumple umbrales.
   - **frontend**: Node LTS, cache npm, `npm ci`, `ng lint`, `ng test --watch=false --browsers=ChromeHeadless`, `ng build`.
2. Trigger: `push` a main y `pull_request`.
3. Badge de estado en README raíz.

## Fuera de alcance
Despliegue continuo, publicación de imágenes Docker (T1001).

## Criterios de aceptación
- Workflow sintácticamente válido (`act` opcional o revisión manual) y verde en la primera ejecución real.
- La cobertura configurada en T101 se verifica en CI.

## Ficheros previstos
`.github/workflows/ci.yml`, `README.md` (badge).
