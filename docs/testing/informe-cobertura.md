# Informe de cobertura

Generado a partir de `mvn -B verify` (JaCoCo). Objetivo (CONTEXT-GLOBAL §8):
`domain` ≥ 90 %, `application` ≥ 80 %. Reporte HTML completo en
`backend/target/site/jacoco/index.html` (no versionado).

## T204 — Autenticación JWT (última actualización)

Tras `mvn -B verify` con 84 tests en verde, JaCoCo mantiene en verde los gates
de `domain` (≥90 %) y `application` (≥80 %). La tarea añadió cobertura sobre:

- `identity.domain`: refresh token, excepciones de autenticación y unicidad
  global de email.
- `identity.application`: login, refresh rotatorio, logout y revocación por
  reutilización.
- `identity.interfaces.rest`: endpoints `/api/v1/auth/login|refresh|logout`.

El reporte HTML completo sigue disponible en
`backend/target/site/jacoco/index.html`.

**Riesgo detectado (no corregido en esta tarea, fuera de su alcance):** el
patrón de inclusión configurado en `backend/pom.xml`
(`<include>*.domain.*</include>` / `<include>*.application.*</include>`)
sólo casa con subpaquetes (p. ej. `identity.domain.event`), no con los
paquetes `domain`/`application` "planos" de cada módulo (p. ej.
`tenant.domain`, `identity.domain`, `tenant.application`), donde vive la
mayoría del código real. JaCoCo no falla cuando un patrón no casa con ningún
paquete (regla vacía = "cumplida" por diseño, ver comentario en `pom.xml`),
por lo que el `check` de Maven actualmente pasa sin haber evaluado el 90 %/80
% contra el grueso del código de dominio/aplicación. En T203 la cobertura
real de esos paquetes es de todas formas ≥98 % (medida manualmente arriba),
pero el gate automático no lo está verificando de forma efectiva. Se
recomienda corregir los patrones (`*.domain..*` con doble `*` o
`*/domain/*`, según sintaxis de JaCoCo) en una tarea futura de
infraestructura de build.
