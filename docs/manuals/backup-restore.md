# Backup y restauración de PostgreSQL

Procedimiento operativo de la épica T150. La estrategia (frecuencia, retención,
cifrado, responsables y objetivos RPO/RTO) está en
[ADR-0013](../adr/ADR-0013-estrategia-backup-retencion.md); este documento es el
manual de ejecución.

Requisitos cubiertos: RO-005, RO-006, RO-007, RT-008.

## 1. Configuración

Variables que leen los scripts (del entorno o del `.env`; el entorno tiene
prioridad):

| Variable | Por defecto | Para qué |
|---|---|---|
| `BACKUP_DIR` | `./backups` | Destino de los volcados y de los logs |
| `BACKUP_RETENTION_DAYS` | `7` | Días de backups diarios que se conservan |
| `BACKUP_RETENTION_WEEKLY` | `4` | Semanas de backups semanales que se conservan |
| `BACKUP_PASSPHRASE` | — | Passphrase GPG del cifrado. **Obligatoria** salvo `--no-encrypt` |
| `POSTGRES_DB`, `POSTGRES_USER` | `timetracking` | Base y usuario |
| `COMPOSE_POSTGRES_SERVICE` | `postgres` | Servicio de compose |

`BACKUP_PASSPHRASE` **no** debe guardarse en el `.env` junto a los backups ni en
el mismo disco: vive en el gestor de secretos del entorno. Un backup cifrado con
la clave al lado no está cifrado.

> Si se pierde `BACKUP_PASSPHRASE`, todos los backups cifrados son irrecuperables.
> No hay mecanismo de recuperación: es una clave simétrica.

## 2. Backup

### Manual

```bash
export BACKUP_PASSPHRASE='...'   # desde el gestor de secretos
scripts/backup/backup-postgres.sh
```

Deja en `${BACKUP_DIR}`:

* `timetracking-<db>-<YYYYMMDDTHHMMSSZ>.dump.gpg` — volcado `pg_dump -Fc`
  cifrado con AES-256, permisos `600`.
* `...dump.gpg.sha256` — para verificar la integridad antes de restaurar.
* `logs/<mismo-nombre>.log` — traza de la ejecución.

Códigos de salida: `0` correcto · `1` fallo del volcado, del cifrado o de la
verificación · `2` error de configuración o requisitos previos.

Ante cualquier fallo el script **borra el fichero parcial**, para que la
rotación no lo tome nunca por un backup válido.

### Programado (diario, 03:00)

```cron
0 3 * * * cd /opt/timetracking && BACKUP_PASSPHRASE="$(cat /etc/timetracking/backup.pass)" \
  scripts/backup/backup-postgres.sh >> /var/log/timetracking-backup.log 2>&1
```

El código de salida es significativo: encadena el cron con la alerta de
Operación. Un backup que falla en silencio es exactamente el escenario que este
trabajo pretende evitar.

### Copia remota

El script **no** sube nada fuera del host: el destino depende de cada
instalación. Operación sincroniza `${BACKUP_DIR}` a un almacenamiento externo
después de cada backup, por ejemplo:

```cron
30 3 * * * rclone sync /opt/timetracking/backups remoto:timetracking-backups
```

Sin esta segunda copia, un fallo del disco del host se lleva la base y los
backups a la vez.

## 3. Restauración

Procedimiento de diseño §20.2: **detener aplicación → restaurar → validar →
arrancar → smoke tests**. Los cuatro primeros pasos los ejecuta el script; el
quinto es `scripts/smoke.sh`.

```bash
export BACKUP_PASSPHRASE='...'
scripts/backup/restore-postgres.sh backups/timetracking-timetracking-20260804T122141Z.dump.gpg
bash scripts/smoke.sh
```

`--yes` salta la confirmación interactiva (solo para automatizar simulacros).

> **La restauración es destructiva**: hace `DROP DATABASE` y `CREATE DATABASE`.
> Todo lo que haya en la base destino se pierde. Si el estado actual puede tener
> algún valor forense, haz un backup del estado dañado *antes* de restaurar.

Qué hace el script, paso a paso:

1. **Verifica el SHA-256** del backup. Si no coincide, aborta: el fichero está
   corrupto o manipulado.
2. **Descifra** el `.gpg` en un directorio temporal que se borra siempre, aunque
   la restauración falle (el volcado en claro contiene datos personales).
3. **Detiene `backend` y `frontend`**. Es obligatorio antes de tocar la base: un
   backend vivo mantiene conexiones que impiden el `DROP DATABASE` y, peor, puede
   escribir sobre una base a medio restaurar.
4. **Recrea la base** tras expulsar las conexiones residuales, y ejecuta
   `pg_restore --exit-on-error`.
5. **Valida**: hay tablas en `public`, ninguna migración Flyway marcada como
   fallida, y las tablas `tenant` y `app_user` existen y tienen filas.
6. **Arranca** backend y frontend y espera a `/actuator/health`.

Códigos de salida: `0` restaurado y validado · `1` fallo de restauración o
validación · `2` error de configuración, argumentos o cancelación del operador.

### Después de restaurar

* Ejecuta `bash scripts/smoke.sh` y anota el resultado.
* Comprueba que el `JWT_SECRET` del `.env` es el mismo que estaba activo cuando
  se tomó el backup. Si no lo es, todas las sesiones y refresh tokens
  restaurados quedan invalidados y los usuarios tendrán que volver a entrar.
* Revisa `outbox_message` en estado `PENDING`: son eventos que el publicador
  reintentará. La entrega es *at-least-once* y los consumidores son idempotentes
  (ADR-0005), así que la reemisión es segura.

## 4. Simulacro de restauración (RT-008)

Obligatorio **una vez por versión relevante y, como mínimo, trimestralmente**.
Cada ejecución añade una entrada abajo. Un backup que no se ha restaurado nunca
no es un backup: es un fichero.

### 2026-08-04 — Simulacro T150-04

| Campo | Valor |
|---|---|
| **Fecha y hora** | 2026-08-04, 12:22:59–12:23:12 UTC |
| **Entorno** | Docker Compose local (Docker 29.7.1, PostgreSQL 16.14, backend y frontend construidos del árbol de trabajo) |
| **Ejecutado por** | Agente A4, Ola 1 V2 |
| **Backup usado** | `timetracking-timetracking-20260804T122141Z.dump.gpg` (24 011 B en claro, 7,1 KiB cifrado, SHA-256 `6d974217db73e0fa66c8e3a6991f43f7ed3dd5d53b1c64459727c2fbc1319064`) |
| **Escenario simulado** | Pérdida de datos por `TRUNCATE tenant CASCADE`: se dejó la base con 0 tenants y 0 usuarios (el esquema intacto), imitando un borrado lógico accidental |
| **Estado previo al desastre** | 3 tenants, 5 usuarios, 8 mensajes de outbox, 9 migraciones Flyway |
| **Tiempo de restauración** | **13 s** de extremo a extremo (verificación + descifrado + parada + `pg_restore` + validación + arranque y health del backend). El `pg_restore` en sí tardó < 1 s; los 11 s restantes fueron el arranque del backend |
| **Resultado** | **Correcto.** Estado tras restaurar: 3 tenants, 5 usuarios, 8 mensajes de outbox, 9 migraciones Flyway aplicadas y 0 fallidas, 11 tablas en `public` |
| **Verificación funcional** | `POST /api/v1/auth/login` con `admin-a@acme.test` → HTTP 200; con `employee-b@acme.test` → HTTP 200. Las credenciales anteriores al desastre siguen siendo válidas |
| **Smoke tests** | `bash scripts/smoke.sh` → salida 0, «Smoke test completado correctamente» (health del backend, frontend servido y registro de un tenant nuevo) |
| **Log** | `backups/logs/restore-20260804T122259Z.log` |

**Incidencias detectadas y corregidas durante el simulacro:**

1. La verificación de integridad del backup fallaba con
   `pg_restore: error: could not open input file "-"`. `pg_restore` no acepta
   `-` como nombre de fichero para stdin; hay que invocarlo sin argumento de
   fichero. Corregido en `backup-postgres.sh` y reejecutado. **Esta es
   exactamente la clase de fallo que solo aparece ejecutando el procedimiento**:
   el script habría abortado todos los backups en producción.

**Limitaciones conocidas de este simulacro (declaradas, no disimuladas):**

* El conjunto de datos era de demo (24 KB). Los tiempos **no** extrapolan a un
  volumen de producción; el `pg_restore` crece con el tamaño de la base.
* Se simuló un borrado lógico con el esquema intacto, no la pérdida total del
  volumen `postgres_data` ni un fallo del host.
* No se probó la restauración desde la copia remota: en el entorno del simulacro
  no hay almacenamiento externo configurado.
* No se probó la restauración en una máquina distinta a la de origen.

**Próximo simulacro previsto:** 2026-11-04 (trimestral) o antes si hay una
versión relevante.

## 5. Qué NO cubre el backup

* El fichero `.env`: `JWT_SECRET`, `POSTGRES_PASSWORD`, `PLATFORM_ADMIN_*` y
  `BACKUP_PASSPHRASE` deben custodiarse en el gestor de secretos del entorno.
  Restaurar la base sin el `JWT_SECRET` original invalida todas las sesiones.
* Las imágenes Docker: se reconstruyen del repositorio.
* Los logs de la aplicación.
