# Informe de cobertura

Generado a partir de `mvn -B verify` (JaCoCo). Objetivo (CONTEXT-GLOBAL §8):
`domain` ≥ 90 %, `application` ≥ 80 %. Reporte HTML completo en
`backend/target/site/jacoco/index.html` (no versionado).

## T203 — RegisterTenant (última actualización)

Cifras de línea (`LINE`) por paquete, extraídas de
`backend/target/site/jacoco/jacoco.xml` tras `mvn -B verify` con 65 tests en
verde:

| Paquete | Cobertura de línea |
|---|---|
| `tenant.domain` | 49/49 = 100 % |
| `tenant.domain.event` | 1/1 = 100 % |
| `identity.domain` | 115/117 = 98.29 % |
| `identity.domain.event` | 4/4 = 100 % |
| `tenant.application` | 31/31 = 100 % |
| `shared.domain` | 7/7 = 100 % |

Ambos umbrales del checklist (dominio ≥90 %, aplicación ≥80 %) se cumplen
ampliamente en el código añadido por T203, y el gate real de JaCoCo en Maven
queda en verde.

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
