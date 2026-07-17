# T902 — Hardening de seguridad y revisión OWASP

- Iteración: 9 · Depende de: T901 · Contexto: CONTEXT-GLOBAL §6-7, SDD §11

## Objetivo
Revisión sistemática de seguridad del MVP completo y corrección de hallazgos.

## Detalle
1. Checklist OWASP Top 10 aplicado al proyecto; resultado documentado en `docs/security/owasp-review.md` (por categoría: aplica/no aplica, mitigación, evidencia/test).
2. Verificaciones concretas (corregir lo que falle):
   - Cabeceras: `X-Content-Type-Options`, `X-Frame-Options`/CSP, `Referrer-Policy`, `Cache-Control` en respuestas sensibles.
   - CORS solo al origen del frontend; sin `*`.
   - Ningún endpoint sin autorización explícita (test que recorre el mapping de rutas y verifica que todo lo no-público exige auth).
   - Problem Details nunca filtra stack traces ni SQL (test con excepción inesperada forzada → 500 genérico).
   - Logs sin tokens/contraseñas/hashes (revisión + test de no-aparición en login).
   - Dependencias: ejecutar un análisis de vulnerabilidades (p. ej. `mvn org.owasp:dependency-check-maven:check` o `npm audit`) y actualizar/documentar excepciones.
   - Enumeración de usuarios: login y register no revelan si un email existe (mensajes uniformes).
   - Validación de tamaño de payloads y de `reason`/`resolutionComment` (límites).
3. Actualizar el modelo de amenazas (`docs/security/threat-model.md`) con el estado final.

## Criterios de aceptación
- `mvn verify` verde; hallazgos corregidos o registrados como riesgo aceptado con justificación; informe OWASP completo.

## Ficheros previstos
`docs/security/owasp-review.md`, `threat-model.md`, correcciones puntuales + tests.
