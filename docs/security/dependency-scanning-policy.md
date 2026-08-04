# Política de escaneo de secretos y dependencias

Cubre T30-05 y los requisitos RS-015 (la CI analiza dependencias vulnerables) y
RS-016 (la CI detecta secretos). Es la referencia normativa para decidir cuándo
un hallazgo rompe el build y cómo se tramita una excepción.

## 1. Qué se escanea y dónde

| Escáner | Job de CI | Alcance | Configuración |
|---|---|---|---|
| gitleaks 8.30.1 | `secret-scan` | **Toda la historia de git** (`fetch-depth: 0`) | `.gitleaks.toml` |
| `npm audit` + gate | `frontend-dependencies` | Dependencias del frontend, runtime y build | `scripts/security/npm-audit-gate.sh`, `scripts/security/npm-audit-allowlist.txt` |
| OWASP dependency-check 13.0.0 | `backend-dependencies` | Dependencias Maven del backend | `backend/dependency-check-suppressions.xml` |

Los tres corren en cada `push` a `main` y en cada pull request, y además
semanalmente (`cron '17 4 * * 1'`): una dependencia sin cambios puede volverse
vulnerable sin que nadie toque el repositorio.

## 2. Política de severidad

### Secretos (gitleaks)

**Cualquier hallazgo rompe el build. No hay umbral.** Un secreto es binario: o
está expuesto o no lo está.

Se escanea la historia completa, no solo el diff, porque un secreto borrado en
el commit siguiente sigue estando comprometido: quien clonó el repositorio entre
medias lo tiene.

### Dependencias

| Severidad | Frontend (`npm audit`) | Backend (dependency-check) | Efecto |
|---|---|---|---|
| `critical` | sí | CVSS ≥ 9.0 | **Rompe el build** |
| `high` | sí | CVSS ≥ 7.0 | **Rompe el build** |
| `moderate` | no | CVSS 4.0–6.9 | Se informa en el artefacto; se revisa en la revisión trimestral |
| `low` / `info` | no | CVSS < 4.0 | Solo informativo |

El umbral es **high** en ambos, deliberadamente alineado: no tiene sentido que
el mismo CVE bloquee en un lado y pase en el otro.

Está **prohibido** desbloquear un build bajando el umbral (`--audit-level`,
`failBuildOnCVSS`) o desactivando un escáner. El único mecanismo admitido es la
excepción nominal descrita abajo.

## 3. Excepciones

### Principio

Una excepción es una decisión de riesgo, no un truco para poner el build en
verde. Por eso toda excepción es **nominal** (un advisory o CVE concreto),
**justificada** (por qué no es explotable *en este sistema*) y **caduca** (tiene
fecha de revisión y el gate vuelve a fallar cuando pasa).

Está prohibido excluir por paquete sin acotar el identificador, por directorio,
por extensión o por severidad. Una exclusión amplia convierte el escáner en
decorativo.

### Cómo se documenta

| Escáner | Fichero | Formato |
|---|---|---|
| gitleaks | `.gitleaks.toml`, bloque `[[allowlists]]` | Literal exacto del placeholder + comentario con la justificación |
| `npm audit` | `scripts/security/npm-audit-allowlist.txt` | `GHSA-id\|paquete\|ambito\|fecha-revision\|motivo` |
| dependency-check | `backend/dependency-check-suppressions.xml` | `<suppress until="YYYY-MM-DD">` con `<cve>` y `<notes>` |

En los tres casos la fecha de revisión es de **como máximo 3 meses**. Pasada esa
fecha:

* `npm-audit-gate.sh` falla explícitamente con «excepción caducada».
* dependency-check ignora el `<suppress>` y el hallazgo vuelve a bloquear.
* Las entradas de `.gitleaks.toml` se revisan en la misma revisión trimestral.

El gate de npm también avisa cuando una excepción **ya no es necesaria** (el
advisory desapareció): esas líneas deben retirarse de la allowlist.

### Quién aprueba

| Situación | Aprobación |
|---|---|
| Excepción de severidad `high` en dependencia **de build** (no llega al usuario) | Revisor del pull request, con la justificación escrita en el fichero de excepciones |
| Excepción de severidad `high` en dependencia **de runtime** | Responsable de seguridad del proyecto **y** revisor del PR |
| Excepción de severidad `critical`, cualquier ámbito | Responsable de seguridad **y** responsable del producto. Requiere plan de corrección con fecha |
| Excepción sobre un hallazgo de **gitleaks** | Responsable de seguridad, siempre. Antes hay que confirmar que el valor no es un secreto real y, si lo fuera, **rotarlo** |
| Renovar una excepción caducada | El mismo nivel que la original; no se renueva "por defecto" |

En un proyecto de tamaño académico como este, «responsable de seguridad» es el
rol asignado en el equipo, no necesariamente una persona distinta del revisor;
lo que no es admisible es que quien introduce la excepción sea su único
aprobador.

### Qué mirar antes de aprobar

1. ¿Existe versión corregida? Si la hay y no es *breaking*, se actualiza. No se
   tramita excepción por comodidad.
2. ¿El código vulnerable es **alcanzable** desde esta aplicación? La
   justificación tiene que decirlo con concreción ("la app no usa SSR, así que
   `HttpTransferCache` nunca se instancia"), no en genérico ("no nos afecta").
3. ¿Es runtime o build? Una vulnerabilidad en la cadena de construcción no llega
   al navegador del usuario, pero sí compromete la CI: no es inocua.
4. ¿Qué plan hay y para cuándo?

## 4. Estado actual (2026-08-04)

### Secretos

`gitleaks git .` sobre 124 commits: **0 hallazgos** con `.gitleaks.toml`.

Sin configuración se detectaban 6 hallazgos, todos placeholders confirmados como
no-secretos:

* `replace-with-at-least-32-bytes-secret` en `.env.example` y
  `backend/.env.example` (valor de relleno de `JWT_SECRET`).
* `test-jwt-secret-key-with-at-least-32-bytes` en `application-test.yml`
  (secreto fijo del perfil de test, público por diseño).

Se excluye el **literal**, no el fichero: si mañana alguien commitea un
`JWT_SECRET` real en `.env.example`, su valor no coincidirá con el placeholder y
el escáner seguirá fallando. Verificado con una prueba negativa (se inyectó una
clave AWS de ejemplo en `.env.example` y gitleaks la detectó).

### Frontend

`npm audit`: 32 vulnerabilidades (1 critical, 22 high, 7 moderate, 2 low).
**24 advisories high/critical**, todos con excepción aprobada y fecha de revisión
2026-11-04. Ninguno es resoluble sin modificar `frontend/package.json` o
`frontend/package-lock.json`, ficheros reservados al agente principal
(ver `HANDOFF.md`):

* **5 advisories de runtime**, todos en la cadena Angular 19
  (`@angular/common`, `@angular/core`). El único fix es Angular 21, que es un
  salto de dos versiones mayores. Los tres vectores (HttpTransferCache,
  hidratación de cliente, i18n) exigen funcionalidades que esta SPA no activa.
* **19 advisories de build** (`tar`, `vite`, `postcss`, `piscina`,
  `serialize-javascript`, `http-proxy-middleware`, `sigstore`,
  `brace-expansion`, `fast-uri`, `ip-address`). No se empaquetan en la imagen del
  frontend. Cinco de ellos (`brace-expansion`, `fast-uri`, `ip-address`) tienen
  fix **no breaking**: se corrigen con un `npm audit fix` que actualice solo el
  lockfile, pendiente del agente propietario del fichero.

### Backend

OWASP dependency-check **aún no ha ejecutado un análisis completo**: requiere el
secreto `NVD_API_KEY` dado de alta en GitHub. Sin él, la API del NIST limita a
unas 5 peticiones cada 30 segundos y la primera sincronización de la base de
vulnerabilidades no termina en un tiempo razonable.

Mientras el secreto no exista, el job emite un `::warning` visible y **no
bloquea**. Es una decisión consciente: hacerlo fallar produciría un rojo
permanente en cada PR y en cada fork, que es la vía más rápida para que el equipo
aprenda a ignorar la CI. El alta del secreto está declarada en `HANDOFF.md` como
acción pendiente del agente principal, y hasta que se resuelva **RS-015 solo está
cubierto para el frontend**.

## 5. Operación

### Ejecutar los escáneres en local

```bash
# Secretos (requiere gitleaks >= 8.30.0 en el PATH)
gitleaks git . --config .gitleaks.toml --redact

# Dependencias del frontend (mismo gate que la CI)
scripts/security/npm-audit-gate.sh

# Dependencias del backend (requiere NVD_API_KEY)
cd backend && mvn -B org.owasp:dependency-check-maven:13.0.0:check \
  -DnvdApiKey="$NVD_API_KEY" -DfailBuildOnCVSS=7 \
  -DsuppressionFiles=dependency-check-suppressions.xml
```

Los informes quedan en `security-reports/` (frontend) y
`backend/target/` (backend); ambos están en `.gitignore`.

### Secretos a dar de alta en GitHub

| Secreto | Dónde | Para qué |
|---|---|---|
| `NVD_API_KEY` | *Settings → Secrets and variables → Actions* del repositorio | Clave gratuita de la API del NIST (https://nvd.nist.gov/developers/request-an-api-key). Sin ella el análisis de dependencias del backend no se ejecuta |

gitleaks se instala como binario con checksum verificado, así que **no** requiere
la licencia de `gitleaks-action`.

### Revisión periódica

Trimestral, junto al simulacro de restauración (ADR-0013):

1. Revisar cada excepción vigente: ¿sigue siendo cierta la justificación?
2. Retirar las que el gate marque como innecesarias.
3. Revisar los hallazgos `moderate` acumulados.
4. Renovar o corregir las que caduquen.
