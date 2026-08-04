# HANDOFF — Agente A4 (Ola 1): T30-05 escaneo en CI + T150 backups y restauración

Entrega de infraestructura y documentación. **No se ha tocado código Java de
negocio ni se ha añadido ninguna migración Flyway.**

---

## 0. AVISO PREVIO: el worktree parte de un `main` anterior a la Ola 0

Esto condiciona el merge y hay que leerlo antes que nada.

El worktree de A4 se creó desde `6370dd2` (merge de `fix/backend-concurrencia-admin`),
que es **anterior** a la rama `feat/v2-administracion-tenants`. En consecuencia,
en la base de A4 **no existen**:

* `docs/traceability/requirements-matrix.md`
* `docs/adr/ADR-0010-...` y `ADR-0011`/`ADR-0012`
* `docs/agents/reservas-v2.md`
* `docs/api/openapi.yaml` con `/api/v1/platform/**`
* Las variables `PLATFORM_ADMIN_*` y `PUBLIC_REGISTRATION_ENABLED` en
  `.env.example` y `docker-compose.yml`
* El servicio `mailpit`

Consecuencias concretas para el merge:

1. **`docs/traceability/requirements-matrix.md`**: A4 lo ha *creado* con solo sus
   dos secciones nuevas y una cabecera de aviso. Al mergear, **copia esas dos
   secciones al final de la matriz real y descarta el fichero de A4 con su
   cabecera**. No hay filas ajenas modificadas.
2. **`docs/manuals/operations.md`**: A4 ha documentado `PLATFORM_ADMIN_EMAIL`,
   `PLATFORM_ADMIN_PASSWORD` y `PUBLIC_REGISTRATION_ENABLED` según lo descrito en
   la planificación y en el `.env.example` de la rama V2, **sin haber podido
   leer el código que los implementa**. Conviene una revisión rápida de esa
   sección contra el comportamiento real de `PlatformAdminBootstrap` y del flag
   de registro público.
3. **`.gitleaks.toml`**: la allowlist se validó contra el `.env.example` de la
   base antigua. El `.env.example` de la rama V2 añade `PLATFORM_ADMIN_PASSWORD=`
   (vacío) y `MAIL_*`, que **no** generan hallazgos. Verificado leyendo el
   fichero de la rama V2; conviene reejecutar `gitleaks git .` tras el merge.
4. La ADR elegida es **ADR-0013**, dentro del rango 0013–0017 reservado a la
   Ola 1.

---

## 1. Ficheros prohibidos: cambios que debe aplicar el agente principal

### 1.1 `backend/pom.xml` — perfil de OWASP dependency-check (OPCIONAL)

**No es imprescindible.** El job `backend-dependencies` de la CI invoca el
plugin **por coordenadas** (`mvn org.owasp:dependency-check-maven:13.0.0:check`),
que funciona sin declararlo en el `pom.xml` y no ralentiza el `mvn verify` local.

Si aun así se prefiere declararlo (para fijar la configuración en un solo sitio
y poder lanzarlo con `mvn -Psecurity verify`), añadir dentro de `<project>`:

```xml
  <profiles>
    <!--
      Analisis de dependencias vulnerables (T30-05, RS-015).
      En un perfil desactivado por defecto a proposito: dependency-check
      descarga y sincroniza la base NVD, lo que anadiria minutos a cada
      `mvn verify` local. Se activa con `mvn -Psecurity verify` o desde el job
      `backend-dependencies` de la CI.
    -->
    <profile>
      <id>security</id>
      <build>
        <plugins>
          <plugin>
            <groupId>org.owasp</groupId>
            <artifactId>dependency-check-maven</artifactId>
            <version>13.0.0</version>
            <configuration>
              <!-- CVSS 7.0 = severidad "high", alineado con el gate de npm audit. -->
              <failBuildOnCVSS>7</failBuildOnCVSS>
              <!-- Unico mecanismo admitido para dejar pasar un hallazgo. -->
              <suppressionFiles>
                <suppressionFile>dependency-check-suppressions.xml</suppressionFile>
              </suppressionFiles>
              <formats>
                <format>HTML</format>
                <format>JSON</format>
              </formats>
              <!-- Analizadores innecesarios en un proyecto Java puro. -->
              <assemblyAnalyzerEnabled>false</assemblyAnalyzerEnabled>
              <ossindexAnalyzerEnabled>false</ossindexAnalyzerEnabled>
              <!-- Clave de la API del NIST; sin ella la sincronizacion inicial
                   no termina en un tiempo razonable. -->
              <nvdApiKey>${env.NVD_API_KEY}</nvdApiKey>
            </configuration>
            <executions>
              <execution>
                <goals>
                  <goal>check</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
    </profile>
  </profiles>
```

Si se aplica, cambiar en `.github/workflows/ci.yml` el paso
`OWASP dependency-check` por `mvn -B -Psecurity verify -DskipTests` (o dejar la
invocación por coordenadas, que sigue siendo válida).

El fichero de supresiones **ya está creado** en
`backend/dependency-check-suppressions.xml` (no es un fichero prohibido).

### 1.2 `frontend/package-lock.json` — `npm audit fix` no breaking (RECOMENDADO)

Cinco advisories *high* de la allowlist tienen corrección **sin cambios
incompatibles**, que solo requiere actualizar el lockfile:

```bash
cd frontend && npm audit fix   # SIN --force
```

Resuelve: `brace-expansion` (GHSA-mh99-v99m-4gvg, GHSA-rgw5-rvv9-x895),
`fast-uri` (GHSA-7p8r-x3mc-p8w7, GHSA-v2hh-gcrm-f6hx) e `ip-address`
(GHSA-mwp4-54f8-5fhr).

Después de aplicarlo, **retirar esas 5 líneas** de
`scripts/security/npm-audit-allowlist.txt`. El gate avisa por su cuenta cuando
una excepción ha dejado de ser necesaria.

**No ejecutar `npm audit fix --force`**: arrastraría Angular 19 → 21, dos
versiones mayores, con la migración que eso implica.

### 1.3 `frontend/package.json` — Angular 19 → 21 (DECISIÓN DE PRODUCTO)

Los 5 advisories *high* de **runtime** que quedan (`@angular/common`,
`@angular/core`) solo se corrigen actualizando a Angular 21. Es una migración de
dos versiones mayores, fuera del alcance de A4 y de la Ola 1. Documentado como
excepción con revisión el **2026-11-04** en
`scripts/security/npm-audit-allowlist.txt`, con la justificación de por qué los
tres vectores (HttpTransferCache, hidratación de cliente, i18n) no son
alcanzables en esta SPA.

### 1.4 `docker-compose.yml` — NO se necesita ningún cambio

El backup se ejecuta **desde el host** con `docker compose exec -T postgres
pg_dump`, y los volcados se escriben en `${BACKUP_DIR}` (por defecto
`./backups`) del anfitrión, **fuera** del volumen de Docker. Esto es deliberado
(ADR-0013): si los backups vivieran dentro de `postgres_data`, un
`docker compose down -v` se llevaría la base y las copias a la vez.

No hay servicio nuevo, ni volumen nuevo, ni puerto nuevo. **No se ha reservado
ningún puerto.**

### 1.5 `.env.example` — variables de backup (OPCIONAL, recomendado)

Los scripts funcionan con valores por defecto, pero conviene documentarlas.
Añadir al final:

```bash
# Backups de PostgreSQL (T150, ADR-0013). Ver docs/manuals/backup-restore.md.
BACKUP_DIR=./backups
BACKUP_RETENTION_DAYS=7
BACKUP_RETENTION_WEEKLY=4

# Passphrase GPG del cifrado de los backups. DEJAR VACIA en .env.example: es un
# secreto y debe venir del gestor de secretos del entorno, nunca de un fichero
# que viva junto a los backups. Si se pierde, los backups cifrados son
# irrecuperables.
BACKUP_PASSPHRASE=
```

### 1.6 `docs/api/openapi.yaml`, `application.yml`, `SecurityConfig`, tests de `architecture/`

**Sin cambios.** A4 no añade endpoints, ni propiedades de Spring, ni rutas
públicas, ni reglas de arquitectura.

---

## 2. Secretos a dar de alta en GitHub

| Secreto | Ubicación | Obligatorio | Consecuencia de no darlo de alta |
|---|---|---|---|
| `NVD_API_KEY` | *Settings → Secrets and variables → Actions* del repositorio | **Sí, para cerrar RS-015 en el backend** | El job `backend-dependencies` emite un `::warning` y **se omite**. La CI no falla, pero el backend queda sin análisis de dependencias |

La clave es gratuita: https://nvd.nist.gov/developers/request-an-api-key
(alta por correo, se recibe en minutos). Sin ella, la API del NIST limita a unas
5 peticiones cada 30 s y la primera sincronización de la base de vulnerabilidades
no termina en un tiempo razonable.

**No hace falta ningún secreto para gitleaks**: se instala como binario con
checksum SHA-256 verificado, en lugar de usar `gitleaks-action`, que exige
licencia para organizaciones.

`BACKUP_PASSPHRASE` **no** es un secreto de GitHub Actions: es un secreto del
entorno de despliegue (la CI no hace backups).

---

## 3. Resultado real de la prueba de restauración (T150-04)

**Ejecutada de verdad** contra el entorno de Docker Compose de este worktree el
**2026-08-04**. Acta completa en `docs/manuals/backup-restore.md` §4; resumen:

| Campo | Valor |
|---|---|
| Entorno | Docker 29.7.1, Compose v5.4.0, PostgreSQL 16.14, backend y frontend construidos del árbol |
| Backup usado | `timetracking-timetracking-20260804T122141Z.dump.gpg`, 24 011 B en claro, cifrado AES-256, SHA-256 `6d974217db73e0fa66c8e3a6991f43f7ed3dd5d53b1c64459727c2fbc1319064` |
| Escenario | `TRUNCATE tenant CASCADE` → base con 0 tenants y 0 usuarios |
| Estado previo | 3 tenants, 5 usuarios, 8 mensajes de outbox, 9 migraciones Flyway |
| Tiempo | **13 s** de extremo a extremo (`pg_restore` < 1 s; 11 s fueron el arranque del backend) |
| Resultado | **Correcto**: 3 tenants, 5 usuarios, 8 outbox, 9 migraciones aplicadas y 0 fallidas, 11 tablas |
| Verificación funcional | `POST /api/v1/auth/login` con `admin-a@acme.test` → **HTTP 200**; `employee-b@acme.test` → **HTTP 200** |
| Smoke tests | `bash scripts/smoke.sh` → **salida 0** |
| Log | `backups/logs/restore-20260804T122259Z.log` (no versionado) |

### Incidencia detectada y corregida durante el simulacro

La verificación de integridad del backup fallaba con
`pg_restore: error: could not open input file "-"`: `pg_restore` no acepta `-`
como nombre de fichero para stdin, hay que invocarlo sin argumento. Corregido en
`scripts/backup/backup-postgres.sh` y reejecutado.

Vale la pena señalarlo: **el bug habría abortado todos los backups en
producción** y solo apareció al ejecutar el procedimiento de verdad.

### Limitaciones declaradas del simulacro

* Datos de demo (24 KB): los tiempos **no** extrapolan a volumen de producción.
* Se simuló un borrado lógico con el esquema intacto, no la pérdida total del
  volumen `postgres_data` ni un fallo del host.
* No se probó la restauración desde copia remota (no hay almacenamiento externo
  en el entorno del simulacro) ni en una máquina distinta a la de origen.

Además se probó la **rotación** con 9 backups sintéticos fechados entre
2026-06-01 y 2026-08-04: elimina correctamente los que caen fuera de las ventanas
diaria (7 días) y semanal (4 semanas ISO) y conserva el primero de cada semana.

---

## 4. Qué se ha creado o modificado

### Nuevos

| Fichero | Qué es |
|---|---|
| `.gitleaks.toml` | Configuración de gitleaks con allowlist por literal (no por ruta) |
| `scripts/security/npm-audit-gate.sh` | Gate de `npm audit` con allowlist nominal y caducidad |
| `scripts/security/npm-audit-allowlist.txt` | 24 excepciones aprobadas, con ámbito, fecha de revisión y motivo |
| `backend/dependency-check-suppressions.xml` | Supresiones de dependency-check (vacío, con plantilla) |
| `scripts/backup/backup-postgres.sh` | Backup `pg_dump -Fc` + cifrado + verificación + rotación |
| `scripts/backup/restore-postgres.sh` | Restauración: parar → restaurar → validar → arrancar |
| `docs/adr/ADR-0013-estrategia-backup-retencion.md` | Estrategia de backup y retención |
| `docs/manuals/backup-restore.md` | Manual de backup/restauración + acta del simulacro |
| `docs/security/dependency-scanning-policy.md` | Política de severidad, excepciones y aprobaciones |
| `docs/traceability/requirements-matrix.md` | **Solo las secciones de A4** (ver §0) |

### Modificados

| Fichero | Cambio |
|---|---|
| `.github/workflows/ci.yml` | `permissions: contents: read` a nivel de workflow, disparador `schedule` semanal y 3 jobs nuevos: `secret-scan`, `frontend-dependencies`, `backend-dependencies` |
| `docs/manuals/operations.md` | `PLATFORM_ADMIN_*`, `PUBLIC_REGISTRATION_ENABLED`, variables de backup, sección de backup/restauración y de escaneo en CI |
| `docs/security/owasp-review.md` | Se retira el riesgo aceptado de A06 («no hay escáner dedicado»); se añaden dos riesgos nuevos acotados y una sección de escaneo de secretos |
| `docs/acceptance-checklist.md` | Viñetas nuevas en «Seguridad» y en «Operación y demo» |
| `.gitignore` | `security-reports/` y `backups/` |

---

## 5. Riesgos y decisiones que conviene revisar

1. **RS-015 solo está cubierto para el frontend** hasta que se dé de alta
   `NVD_API_KEY`. El job avisa y no bloquea, decisión consciente: hacerlo fallar
   produciría un rojo permanente en cada PR y en cada fork, que es la vía más
   rápida para que el equipo aprenda a ignorar la CI. Documentado como riesgo
   aceptado con revisión el 2026-11-04.
2. **El gate de `npm audit` dejaría pasar el build hoy con 24 excepciones.** Es
   la situación real del repositorio, no un maquillaje: cada una está
   justificada individualmente y caduca el 2026-11-04, momento en el que el gate
   volverá a fallar si nadie las ha revisado. Si el criterio del proyecto es no
   arrancar con excepciones, la alternativa es aplicar §1.2 y §1.3 antes del
   merge.
3. **El backup no cubre el `.env`.** Restaurar la base con un `JWT_SECRET`
   distinto al que estaba activo invalida todas las sesiones y refresh tokens
   restaurados. `JWT_SECRET`, `POSTGRES_PASSWORD`, `PLATFORM_ADMIN_PASSWORD` y
   `BACKUP_PASSPHRASE` deben custodiarse en el gestor de secretos del entorno.
4. **`BACKUP_PASSPHRASE` es un punto único de fallo**: si se pierde, todos los
   backups cifrados son irrecuperables. Es una clave simétrica, no hay
   recuperación. Riesgo asumido en ADR-0013 con responsable explícito.
5. **RPO de 24 horas.** ADR-0013 descarta PITR por coste operativo con una sola
   instancia y sin equipo de operación dedicado (RC-008). Si el negocio no acepta
   perder hasta un día de fichajes, lo que hay que revisar es la ADR, no la
   frecuencia del cron.
6. **La copia remota queda fuera del script** (el destino depende de cada
   instalación). Mientras nadie configure el `rclone`/`rsync` diario, un fallo del
   disco del host se lleva la base y los backups a la vez. Documentado en
   `docs/manuals/backup-restore.md` §2.
7. **`scripts/backup/*.sh` dependen de `docker compose exec`**: solo sirven para
   el despliegue por Compose. Un despliegue gestionado necesitaría revisarlos.
8. **Choque potencial con A5 (T140 observabilidad)**: el diseño §19 menciona
   «último backup» y «última restauración probada» entre las métricas del panel
   de operación. A4 no expone ninguna métrica; los scripts dejan la información
   en `backups/logs/`. Si A5 la quiere, hace falta acordar el formato.
9. **A4 es el único agente autorizado a tocar `.github/workflows/ci.yml`** en
   toda la V2. Cualquier otro agente que necesite un job debe pedirlo, no
   editarlo.
