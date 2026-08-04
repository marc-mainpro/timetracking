#!/usr/bin/env bash
# Backup logico de PostgreSQL (T150-02, RO-005, RO-006).
#
# Estrategia y retencion: docs/adr/ADR-0013-estrategia-backup-retencion.md
# Procedimiento de restauracion: scripts/backup/restore-postgres.sh
#
# Que hace:
#   1. Vuelca la base con `pg_dump -Fc` (formato custom, comprimido y apto para
#      restauracion selectiva) desde el contenedor `postgres` de docker compose.
#   2. Cifra el volcado con GPG simetrico AES-256 si hay clave disponible.
#   3. Calcula SHA-256, verifica la integridad del volcado y escribe un log.
#   4. Rota los backups segun la politica de retencion.
#
# Uso:
#   scripts/backup/backup-postgres.sh [--no-encrypt] [--dry-run]
#
# Variables de entorno (se leen de .env si existe; el entorno tiene prioridad):
#   BACKUP_DIR            destino de los backups        (por defecto ./backups)
#   BACKUP_RETENTION_DAYS dias de retencion de diarios  (por defecto 7)
#   BACKUP_RETENTION_WEEKLY semanales a conservar       (por defecto 4)
#   BACKUP_PASSPHRASE     passphrase GPG para el cifrado (obligatoria salvo
#                         --no-encrypt; nunca se imprime en los logs)
#   POSTGRES_DB, POSTGRES_USER  base y usuario (por defecto timetracking)
#   COMPOSE_POSTGRES_SERVICE    nombre del servicio (por defecto postgres)
#
# Codigos de salida:
#   0  backup creado, verificado y rotado
#   1  fallo del volcado, del cifrado o de la verificacion
#   2  error de configuracion o de requisitos previos

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

ENCRYPT=1
DRY_RUN=0
for arg in "$@"; do
  case "$arg" in
    --no-encrypt) ENCRYPT=0 ;;
    --dry-run) DRY_RUN=1 ;;
    -h | --help)
      sed -n '2,32p' "${BASH_SOURCE[0]}"
      exit 0
      ;;
    *)
      printf 'Argumento desconocido: %s\n' "$arg" >&2
      exit 2
      ;;
  esac
done

# --- Configuracion -----------------------------------------------------------

if [[ -f .env ]]; then
  # Solo se toman las claves que necesita el script; no se exporta el .env
  # entero para no arrastrar secretos ajenos al proceso de backup.
  while IFS='=' read -r key value; do
    case "$key" in
      POSTGRES_DB | POSTGRES_USER | BACKUP_DIR | BACKUP_RETENTION_DAYS | BACKUP_RETENTION_WEEKLY | BACKUP_PASSPHRASE)
        [[ -n ${!key-} ]] || printf -v "$key" '%s' "$value"
        ;;
    esac
  done < <(grep -E '^[A-Z_]+=' .env || true)
fi

POSTGRES_DB="${POSTGRES_DB:-timetracking}"
POSTGRES_USER="${POSTGRES_USER:-timetracking}"
BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
BACKUP_RETENTION_WEEKLY="${BACKUP_RETENTION_WEEKLY:-4}"
COMPOSE_POSTGRES_SERVICE="${COMPOSE_POSTGRES_SERVICE:-postgres}"

LOG_DIR="$BACKUP_DIR/logs"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BASENAME="timetracking-${POSTGRES_DB}-${TIMESTAMP}"
DUMP_FILE="$BACKUP_DIR/${BASENAME}.dump"
LOG_FILE="$LOG_DIR/${BASENAME}.log"

mkdir -p "$BACKUP_DIR" "$LOG_DIR"
# Los backups contienen datos personales de todos los tenants: permisos
# restrictivos desde el primer momento (RS-006, RGPD).
chmod 700 "$BACKUP_DIR"

log() {
  printf '%s [backup] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$LOG_FILE"
}
err() {
  printf '%s [backup] ERROR: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$LOG_FILE" >&2
}

# Cualquier fallo deja el backup a medias: se borra el parcial para que la
# rotacion no lo tome nunca por un backup valido.
cleanup_partial() {
  local status=$?
  if [[ $status -ne 0 ]]; then
    rm -f "$DUMP_FILE" "$DUMP_FILE.gpg" 2>/dev/null || true
    err "backup abortado con codigo $status; se elimino el fichero parcial"
  fi
  return $status
}
trap cleanup_partial EXIT

log "inicio | base=$POSTGRES_DB usuario=$POSTGRES_USER destino=$BACKUP_DIR cifrado=$ENCRYPT"

# --- Requisitos previos ------------------------------------------------------

command -v docker >/dev/null 2>&1 || {
  err 'docker no esta disponible en el PATH'
  exit 2
}

if ! docker compose ps --status running --services 2>/dev/null | grep -qx "$COMPOSE_POSTGRES_SERVICE"; then
  err "el servicio '$COMPOSE_POSTGRES_SERVICE' no esta levantado (docker compose up -d $COMPOSE_POSTGRES_SERVICE)"
  exit 2
fi

if [[ $ENCRYPT -eq 1 ]]; then
  command -v gpg >/dev/null 2>&1 || {
    err 'gpg no esta disponible; instalalo o ejecuta con --no-encrypt (solo para pruebas locales)'
    exit 2
  }
  if [[ -z ${BACKUP_PASSPHRASE:-} ]]; then
    err 'falta BACKUP_PASSPHRASE; definela en el entorno o ejecuta con --no-encrypt (solo pruebas locales)'
    exit 2
  fi
fi

if [[ $DRY_RUN -eq 1 ]]; then
  log "dry-run: se habria generado $DUMP_FILE"
  trap - EXIT
  exit 0
fi

# --- Volcado -----------------------------------------------------------------

START_TS=$(date +%s)

# -Fc: formato custom (comprimido, restauracion selectiva con pg_restore).
# El volcado sale por stdout del contenedor para no dejar copias dentro de el.
if ! docker compose exec -T "$COMPOSE_POSTGRES_SERVICE" \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-privileges \
  >"$DUMP_FILE" 2>>"$LOG_FILE"; then
  err 'pg_dump fallo; revisa el log'
  exit 1
fi

if [[ ! -s $DUMP_FILE ]]; then
  err 'pg_dump genero un fichero vacio'
  exit 1
fi

# Verificacion de integridad: pg_restore --list solo lee el indice del volcado,
# asi que detecta un fichero truncado o corrupto sin restaurar nada.
# pg_restore sin argumento de fichero lee de stdin.
if ! docker compose exec -T "$COMPOSE_POSTGRES_SERVICE" pg_restore --list \
  <"$DUMP_FILE" >/dev/null 2>>"$LOG_FILE"; then
  err 'el volcado no es legible por pg_restore (fichero corrupto o truncado)'
  exit 1
fi

DUMP_BYTES=$(wc -c <"$DUMP_FILE")
log "volcado correcto | fichero=$(basename "$DUMP_FILE") bytes=$DUMP_BYTES"

# --- Cifrado -----------------------------------------------------------------

FINAL_FILE="$DUMP_FILE"
if [[ $ENCRYPT -eq 1 ]]; then
  # --batch + --passphrase-fd 0 evita que la passphrase aparezca en la linea de
  # comandos y por tanto en `ps` o en los logs del shell.
  if ! printf '%s' "$BACKUP_PASSPHRASE" | gpg --batch --yes --quiet \
    --passphrase-fd 0 --pinentry-mode loopback \
    --symmetric --cipher-algo AES256 \
    --output "$DUMP_FILE.gpg" "$DUMP_FILE" 2>>"$LOG_FILE"; then
    err 'el cifrado GPG fallo'
    exit 1
  fi
  rm -f "$DUMP_FILE"
  FINAL_FILE="$DUMP_FILE.gpg"
  log "cifrado correcto | fichero=$(basename "$FINAL_FILE") algoritmo=AES256"
else
  log 'AVISO: backup SIN cifrar (--no-encrypt). Solo admisible en pruebas locales.'
fi

chmod 600 "$FINAL_FILE"
sha256sum "$FINAL_FILE" >"$FINAL_FILE.sha256"
ELAPSED=$(($(date +%s) - START_TS))
log "sha256=$(cut -d' ' -f1 <"$FINAL_FILE.sha256") duracion=${ELAPSED}s"

# --- Rotacion ----------------------------------------------------------------
#
# Politica (ADR-0013): se conservan todos los backups de los ultimos
# BACKUP_RETENTION_DAYS dias y, ademas, el mas antiguo de cada semana ISO hasta
# BACKUP_RETENTION_WEEKLY semanas. Se borra el resto.

log "rotacion | diarios=${BACKUP_RETENTION_DAYS}d semanales=${BACKUP_RETENTION_WEEKLY}"

cutoff_daily=$(date -u -d "${BACKUP_RETENTION_DAYS} days ago" +%Y%m%d)
cutoff_weekly=$(date -u -d "$((BACKUP_RETENTION_WEEKLY * 7)) days ago" +%Y%m%d)
declare -A weekly_keep=()
deleted=0

# Primera pasada: elegir el backup mas antiguo de cada semana ISO como semanal.
while IFS= read -r f; do
  base="$(basename "$f")"
  [[ $base =~ -([0-9]{8})T[0-9]{6}Z\. ]] || continue
  day="${BASH_REMATCH[1]}"
  week=$(date -u -d "$day" +%G-W%V)
  [[ -n ${weekly_keep[$week]:-} ]] || weekly_keep[$week]="$f"
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f \( -name '*.dump' -o -name '*.dump.gpg' \) | sort)

# Segunda pasada: borrar lo que no entra ni en la ventana diaria ni en la
# semanal.
while IFS= read -r f; do
  base="$(basename "$f")"
  [[ $base =~ -([0-9]{8})T[0-9]{6}Z\. ]] || continue
  day="${BASH_REMATCH[1]}"
  [[ $day -ge $cutoff_daily ]] && continue
  week=$(date -u -d "$day" +%G-W%V)
  if [[ ${weekly_keep[$week]:-} == "$f" && $day -ge $cutoff_weekly ]]; then
    continue
  fi
  rm -f "$f" "$f.sha256"
  deleted=$((deleted + 1))
  log "rotado (eliminado) $base"
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f \( -name '*.dump' -o -name '*.dump.gpg' \) | sort)

# Los logs siguen la misma ventana que los backups semanales.
find "$LOG_DIR" -maxdepth 1 -type f -name '*.log' \
  -mtime "+$((BACKUP_RETENTION_WEEKLY * 7))" -delete 2>/dev/null || true

remaining=$(find "$BACKUP_DIR" -maxdepth 1 -type f \( -name '*.dump' -o -name '*.dump.gpg' \) | wc -l)
log "fin | eliminados=$deleted conservados=$remaining backup=$(basename "$FINAL_FILE")"

trap - EXIT
printf '%s\n' "$FINAL_FILE"
exit 0
