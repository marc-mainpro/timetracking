## T104 — Pipeline de CI

### Cambios
- `.github/workflows/ci.yml` (nuevo): workflow `CI` con trigger `push` a `main` y `pull_request`, dos jobs independientes:
  - **backend** (`ubuntu-latest`): checkout → `actions/setup-java@v4` (Temurin 21, cache `maven` con `cache-dependency-path: backend/pom.xml`) → `mvn -B verify` (working-directory `backend`) → `actions/upload-artifact@v4` publicando `backend/target/site/jacoco/` como artefacto `jacoco-report` (`if: always()`, `if-no-files-found: warn`).
  - **frontend** (`ubuntu-latest`): checkout → `actions/setup-node@v4` (Node 20, cache `npm` con `cache-dependency-path: frontend/package-lock.json`) → `npm ci` → `npx ng lint` → `npx ng test --watch=false --browsers=ChromeHeadless` → `npx ng build` (todos con working-directory `frontend`).
- `README.md`: añadido badge de estado (`![CI](.../actions/workflows/ci.yml/badge.svg)`) justo debajo del título, con placeholder `OWNER/REPO` y una nota explícita de que hay que sustituirlo al crear el remote. Se sustituyó la sección "Fuera de alcance de este compose" que mencionaba "CI (GitHub Actions se configura en una tarea aparte)" por una sección `## CI` que documenta los dos jobs y sus pasos; el resto del contenido del README no se tocó.

### Pruebas (comandos ejecutados y resultado)
- No se ejecutó el workflow en GitHub Actions (no hay remote configurado; fuera del alcance posible en este entorno). Validación realizada localmente:
  - `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))"` → sin errores (YAML sintácticamente válido).
  - Revisión manual línea a línea de los comandos del job `backend` (`mvn -B verify`) y `frontend` (`npm ci`, `npx ng lint`, `npx ng test --watch=false --browsers=ChromeHeadless`, `npx ng build`) contra los comandos indicados como ya validados en el enunciado de la tarea (coinciden exactamente).
  - Comprobado que `backend/pom.xml` ya integra JaCoCo (`prepare-agent` en fase test, `report` en fase test, `check-domain-coverage` y `check-application-coverage` en fase `verify` con umbrales 0.90/0.80) — por tanto `mvn -B verify` ya falla si no se cumplen los umbrales de cobertura, sin pasos adicionales en el workflow.
  - Comprobado que `frontend/package-lock.json` existe (requisito de `npm ci`) y que no hay `karma.conf.js` con configuración de navegador que entre en conflicto con `--browsers=ChromeHeadless`.
  - `git status --porcelain` tras los cambios: solo `README.md` modificado y `.github/` (nuevo) — no se tocó nada más del repo.

### Cobertura
No aplica cambio de umbrales; el workflow se limita a invocar `mvn -B verify`, que ya ejecuta y hace cumplir los umbrales de cobertura configurados en T101 (dominio ≥90%, aplicación ≥80%). El informe JaCoCo (`backend/target/site/jacoco/`) se publica como artefacto descargable del job `backend` para inspección manual.

### Seguridad
- No se han añadido secretos al workflow; no se requieren credenciales (no hay pasos de publicación de imágenes ni despliegue, fuera de alcance según la ficha).
- Testcontainers en el job `backend` usa Docker-in-Docker disponible por defecto en runners `ubuntu-latest` de GitHub; no requiere configuración adicional (confirmado por el enunciado de la tarea).
- Chrome headless usado por Karma está preinstalado en `ubuntu-latest`; no se ha añadido instalación manual de Chrome ni variables `CHROME_BIN` (confirmado por el enunciado de la tarea).

### Documentación actualizada
- `README.md`: badge de CI + sección `## CI` describiendo los jobs y pasos del pipeline.

### ADR
No aplica — T104 no introduce ninguna decisión de arquitectura nueva; usa exclusivamente decisiones ya fijadas en CONTEXT-GLOBAL §3 (GitHub Actions: build+tests backend con Testcontainers, build+tests frontend, verificación de cobertura).

### Riesgos detectados
- El badge del README usa el placeholder `OWNER/REPO`. Hasta que no se sustituya por el owner/repo reales al crear el remote de GitHub, el badge no resolverá una imagen válida (se ha dejado una nota explícita justo debajo del título documentando esto).
- El workflow no se ha podido ejecutar realmente en GitHub Actions porque no existe remote configurado todavía (según el estado del repo indicado); la validación de que será "verde en la primera ejecución real" (criterio de aceptación) se apoya en revisión manual de sintaxis y en la coincidencia exacta con los comandos ya validados localmente (`mvn -B verify`, `npm ci`, `npx ng lint`, `npx ng test --watch=false --browsers=ChromeHeadless`, `npx ng build`), no en una ejecución real de CI.

### Pendientes / decisiones que necesitan humano
- Sustituir `OWNER/REPO` en el badge de `README.md` por el owner/repo reales en cuanto se cree el remote de GitHub.
- Ejecutar el workflow una vez exista el remote y confirmar que el primer run real es verde (criterio de aceptación de la ficha), especialmente el comportamiento de Testcontainers en el runner real.
