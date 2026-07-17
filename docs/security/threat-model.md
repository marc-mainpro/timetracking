# Modelo de amenazas inicial (STRIDE resumido)

Ámbito: autenticación, multitenancy y Outbox. Se revisará y ampliará en
T902 (hardening OWASP).

## Autenticación (JWT + refresh token)

| Amenaza STRIDE | Riesgo | Mitigación |
|---|---|---|
| Spoofing | Suplantación de usuario con credenciales robadas o login ambiguo entre tenants con el mismo email | BCrypt para contraseñas, rate limiting de login (Bucket4j, 10 req/min/IP), JWT firmado HS256, email globalmente único (ADR-0008) |
| Tampering | Manipulación del token de acceso | Firma HS256 con secreto en variable de entorno; verificación en cada request vía Spring Security oauth2-resource-server |
| Repudiation | Negar haber realizado una acción | Auditoría de operaciones sensibles (aprobación/rechazo de correcciones) |
| Information disclosure | Fuga del refresh token | Cookie `HttpOnly; Secure; SameSite=Strict`, path `/api/v1/auth`; refresh token opaco y hasheado (SHA-256) en BD; nunca en `localStorage` |
| Denial of service | Fuerza bruta de login | Rate limiting por IP |
| Elevation of privilege | Reutilización de refresh token robado | Rotación de refresh token con detección de reutilización (revocación en cadena) |

## Multitenancy

| Amenaza STRIDE | Riesgo | Mitigación |
|---|---|---|
| Tampering | Cliente envía un `tenant_id` distinto al suyo | El tenant se resuelve SIEMPRE del `TenantContext` derivado del usuario autenticado, nunca de un parámetro del cliente |
| Information disclosure | Fuga de datos entre tenants | Toda tabla de negocio tiene `tenant_id`; toda query filtra por tenant; tests de acceso cruzado obligatorios |
| Elevation of privilege | Un `EMPLOYEE` accede a operaciones de `TENANT_ADMIN` de otro tenant | Autorización por rol Y tenant en cada operación administrativa |

## Outbox

| Amenaza STRIDE | Riesgo | Mitigación |
|---|---|---|
| Tampering | Publicar datos internos (entidades JPA) como eventos | Los eventos de integración se construyen explícitamente a partir de eventos de dominio, nunca de entidades JPA |
| Repudiation | Pérdida de un evento tras fallo | El registro en `outbox_message` ocurre en la misma transacción que el cambio de negocio (atomicidad) |
| Denial of service | Reintentos descontrolados | Entrega at-least-once con consumidores idempotentes; tests de atomicidad y reintentos |

## Pendiente de revisión (T902)

Cabeceras de seguridad HTTP, CORS, dependencias vulnerables (OWASP
Dependency-Check), fuzzing de entradas, revisión de logs para evitar fuga de
secretos.
