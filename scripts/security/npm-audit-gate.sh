#!/usr/bin/env bash
# Gate de `npm audit` para CI (T30-05, RS-015).
#
# Ejecuta `npm audit` sobre frontend/ y falla si aparece algun advisory de
# severidad high o critical que NO este en scripts/security/npm-audit-allowlist.txt,
# o si una excepcion de la allowlist ha superado su fecha de revision.
#
# No usa `npm audit --audit-level=high` a secas porque eso solo deja dos
# opciones: romper siempre el build o silenciar todo. El gate rompe el build
# ante cualquier vulnerabilidad nueva y deja pasar unicamente las excepciones
# aprobadas y con fecha de caducidad.
#
# Uso:
#   scripts/security/npm-audit-gate.sh
#
# Codigos de salida:
#   0  sin hallazgos high/critical fuera de la allowlist
#   1  hallazgos no aprobados o excepciones caducadas
#   2  error de ejecucion (npm ausente, JSON invalido, allowlist ilegible)

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend"
ALLOWLIST="$ROOT_DIR/scripts/security/npm-audit-allowlist.txt"
REPORT_DIR="${NPM_AUDIT_REPORT_DIR:-$ROOT_DIR/security-reports}"

log() { printf '[npm-audit-gate] %s\n' "$*"; }
fail() {
  printf '[npm-audit-gate] ERROR: %s\n' "$*" >&2
  exit 2
}

command -v npm >/dev/null 2>&1 || fail 'npm no esta disponible en el PATH.'
command -v node >/dev/null 2>&1 || fail 'node no esta disponible en el PATH.'
[[ -d $FRONTEND_DIR ]] || fail "no existe $FRONTEND_DIR"
[[ -f $ALLOWLIST ]] || fail "no existe la allowlist $ALLOWLIST"

mkdir -p "$REPORT_DIR"
REPORT_JSON="$REPORT_DIR/npm-audit.json"

log "Ejecutando npm audit en $FRONTEND_DIR"
# npm audit devuelve codigo != 0 cuando encuentra vulnerabilidades; eso no es un
# error de ejecucion, asi que se captura y se evalua el JSON.
set +e
(cd "$FRONTEND_DIR" && npm audit --json) >"$REPORT_JSON" 2>"$REPORT_DIR/npm-audit.err"
audit_status=$?
set -e

if [[ ! -s $REPORT_JSON ]]; then
  cat "$REPORT_DIR/npm-audit.err" >&2 || true
  fail "npm audit no produjo salida (codigo $audit_status)."
fi

log "Informe: $REPORT_JSON"

ALLOWLIST_FILE="$ALLOWLIST" REPORT_FILE="$REPORT_JSON" node -e '
const fs = require("fs");

const today = process.env.GATE_TODAY || new Date().toISOString().slice(0, 10);
const allowlist = new Map();
const rawAllow = fs.readFileSync(process.env.ALLOWLIST_FILE, "utf8");

for (const [i, line] of rawAllow.split("\n").entries()) {
  const text = line.trim();
  if (!text || text.startsWith("#")) continue;
  const parts = text.split("|");
  if (parts.length < 5) {
    console.error(`ERROR: allowlist linea ${i + 1} mal formada: ${text}`);
    process.exit(2);
  }
  const [id, pkg, scope, review, ...reason] = parts;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(review)) {
    console.error(`ERROR: allowlist linea ${i + 1}: fecha de revision invalida "${review}"`);
    process.exit(2);
  }
  allowlist.set(id.trim(), {
    pkg: pkg.trim(),
    scope: scope.trim(),
    review: review.trim(),
    reason: reason.join("|").trim(),
    used: false,
  });
}

let report;
try {
  report = JSON.parse(fs.readFileSync(process.env.REPORT_FILE, "utf8"));
} catch (e) {
  console.error(`ERROR: no se pudo parsear el informe de npm audit: ${e.message}`);
  process.exit(2);
}

if (!report.vulnerabilities) {
  console.error("ERROR: informe sin campo 'vulnerabilities' (npm < 7?).");
  process.exit(2);
}

const BLOCKING = new Set(["high", "critical"]);
const found = new Map();
for (const vuln of Object.values(report.vulnerabilities)) {
  for (const via of vuln.via || []) {
    if (typeof via !== "object" || !BLOCKING.has(via.severity)) continue;
    const id = String(via.url || "").split("/").pop();
    if (!id) continue;
    if (!found.has(id)) {
      found.set(id, { id, severity: via.severity, name: via.name, title: via.title, url: via.url });
    }
  }
}

const notAllowed = [];
const expired = [];
for (const adv of found.values()) {
  const entry = allowlist.get(adv.id);
  if (!entry) {
    notAllowed.push(adv);
    continue;
  }
  entry.used = true;
  if (entry.review < today) expired.push({ ...adv, review: entry.review });
}

const stale = [...allowlist.entries()].filter(([, e]) => !e.used).map(([id]) => id);
const meta = (report.metadata && report.metadata.vulnerabilities) || {};

console.log(`[npm-audit-gate] Resumen npm audit: ${JSON.stringify(meta)}`);
console.log(`[npm-audit-gate] Advisories high/critical detectados: ${found.size}`);
console.log(`[npm-audit-gate] Excepciones aprobadas y vigentes: ${found.size - notAllowed.length - expired.length}`);

if (stale.length) {
  console.log(`[npm-audit-gate] AVISO: excepciones ya innecesarias, retiralas de la allowlist: ${stale.join(", ")}`);
}

if (expired.length) {
  console.error("[npm-audit-gate] EXCEPCIONES CADUCADAS (revisar y renovar o corregir):");
  for (const a of expired) {
    console.error(`  - ${a.id} (${a.name}, ${a.severity}) fecha de revision ${a.review} < ${today}`);
  }
}

if (notAllowed.length) {
  console.error("[npm-audit-gate] VULNERABILIDADES NO APROBADAS:");
  for (const a of notAllowed) {
    console.error(`  - ${a.id} [${a.severity}] ${a.name}: ${a.title}`);
    console.error(`    ${a.url}`);
  }
}

if (expired.length || notAllowed.length) {
  console.error("[npm-audit-gate] Build bloqueado. Corrige la dependencia o tramita la excepcion segun docs/security/dependency-scanning-policy.md");
  process.exit(1);
}

console.log("[npm-audit-gate] OK: sin vulnerabilidades high/critical fuera de la allowlist.");
'
