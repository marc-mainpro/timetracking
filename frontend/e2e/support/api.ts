import { APIRequestContext, request } from '@playwright/test';

export const API_BASE_URL = process.env['E2E_API_URL'] ?? 'http://localhost:8080';
export const MAILPIT_URL = process.env['E2E_MAILPIT_URL'] ?? 'http://localhost:8025';

export interface Actor {
  email: string;
  password: string;
  token: string;
}

export interface TenantActors {
  tenantId: string;
  /** Identificador del empleado: lo necesitan las asignaciones por ámbito. */
  employeeId: string;
  admin: Actor;
  employee: Actor;
}

/** Sufijo único por prueba: la base de datos es compartida entre ejecuciones. */
export function unique(seed: string): string {
  return `${seed}-${Date.now()}-${Math.floor(Math.random() * 10_000)}`;
}

export async function apiContext(): Promise<APIRequestContext> {
  return request.newContext({ baseURL: API_BASE_URL });
}

/**
 * IP de origen distinta por llamada.
 *
 * El login está limitado a 10 intentos por minuto y por IP (RS-007), y una
 * suite E2E hace muchos más desde la misma máquina. Repartir las llamadas entre
 * direcciones del rango de documentación TEST-NET-2 evita chocar con el límite
 * sin relajarlo: la protección sigue exactamente igual de estricta en
 * producción. Es la misma técnica que usa `TestTenantFactory` en el backend.
 */
function testClientIp(): string {
  const octet = () => Math.floor(Math.random() * 254) + 1;
  return `198.51.100.${octet()}`;
}

export async function login(api: APIRequestContext, email: string, password: string): Promise<string> {
  const response = await api.post('/api/v1/auth/login', {
    headers: { 'X-Forwarded-For': testClientIp() },
    data: { email, password }
  });
  if (!response.ok()) {
    throw new Error(`Login fallido para ${email}: ${response.status()} ${await response.text()}`);
  }
  return (await response.json()).accessToken;
}

export function bearer(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}

export function platformCredentials(): { email: string; password: string } {
  const email = process.env['PLATFORM_ADMIN_EMAIL'];
  const password = process.env['PLATFORM_ADMIN_PASSWORD'];
  if (!email || !password) {
    throw new Error('Define PLATFORM_ADMIN_EMAIL y PLATFORM_ADMIN_PASSWORD para ejecutar los E2E.');
  }
  return { email, password };
}

/**
 * Crea un tenant operativo con su administrador y un empleado.
 *
 * Usa la creación desde plataforma porque es la única vía que deja un tenant
 * listo para operar sin pasar por la aprobación de una solicitud. El flujo
 * público completo se prueba aparte, en `registro-publico.spec.ts`.
 */
export async function createTenant(api: APIRequestContext, seed: string): Promise<TenantActors> {
  const platform = platformCredentials();
  const platformToken = await login(api, platform.email, platform.password);

  const suffix = unique(seed);
  const adminEmail = `admin-${suffix}@acme.test`;
  const adminPassword = 'supersecretpwd';

  const created = await api.post('/api/v1/platform/tenants', {
    headers: bearer(platformToken),
    data: {
      tenantName: `Tenant ${suffix}`,
      timezone: 'Europe/Madrid',
      adminEmail,
      adminPassword,
      firstName: 'Admin',
      lastName: seed
    }
  });
  if (!created.ok()) {
    throw new Error(`No se pudo crear el tenant: ${created.status()} ${await created.text()}`);
  }
  const { tenantId } = await created.json();
  const adminToken = await login(api, adminEmail, adminPassword);

  const employeeEmail = `employee-${suffix}@acme.test`;
  const employeePassword = 'employeepwd123';
  const employeeCreated = await api.post('/api/v1/employees', {
    headers: bearer(adminToken),
    data: {
      email: employeeEmail,
      password: employeePassword,
      firstName: 'Employee',
      lastName: seed,
      roles: ['EMPLOYEE']
    }
  });
  if (!employeeCreated.ok()) {
    throw new Error(`No se pudo crear el empleado: ${employeeCreated.status()} ${await employeeCreated.text()}`);
  }
  const employeeId = (await employeeCreated.json()).id;
  const employeeToken = await login(api, employeeEmail, employeePassword);

  return {
    tenantId,
    employeeId,
    admin: { email: adminEmail, password: adminPassword, token: adminToken },
    employee: { email: employeeEmail, password: employeePassword, token: employeeToken }
  };
}

/**
 * Busca en mailpit el último correo dirigido a una dirección.
 *
 * Reintenta porque el envío no es síncrono: el evento viaja por el outbox y lo
 * publica una tarea programada (ADR-0012).
 */
export async function waitForEmail(
  to: string,
  timeoutMs = 40_000
): Promise<{ subject: string; body: string }> {
  const mail = await request.newContext({ baseURL: MAILPIT_URL });
  const deadline = Date.now() + timeoutMs;
  try {
    while (Date.now() < deadline) {
      const response = await mail.get(`/api/v1/search?query=${encodeURIComponent(`to:${to}`)}`);
      if (response.ok()) {
        const { messages } = await response.json();
        if (messages?.length) {
          const detail = await mail.get(`/api/v1/message/${messages[0].ID}`);
          const message = await detail.json();
          return { subject: message.Subject, body: message.Text ?? '' };
        }
      }
      await new Promise((resolve) => setTimeout(resolve, 1_000));
    }
    throw new Error(`No llegó ningún correo a ${to} en ${timeoutMs} ms`);
  } finally {
    await mail.dispose();
  }
}
