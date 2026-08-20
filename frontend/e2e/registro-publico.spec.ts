import { APIRequestContext, expect, test } from '@playwright/test';

import { apiContext, bearer, login, platformCredentials, unique, waitForEmail } from './support/api';

/**
 * Flujo de alta pública de extremo a extremo (RF-REG-001..006, T160-01).
 *
 * Es el recorrido más largo del producto y el que más capas atraviesa: API,
 * base de datos, outbox, envío de correo y administración de plataforma. Que
 * pase aquí es la única forma de saber que las cuatro piezas encajan; ninguna
 * prueba de integración por separado lo demuestra.
 */
test.describe('Alta pública de tenant', () => {
  let api: APIRequestContext;

  test.beforeAll(async () => {
    api = await apiContext();
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('solicitud, verificación por correo, aprobación y primer acceso', async () => {
    const suffix = unique('e2e-registro');
    const email = `owner-${suffix}@acme.test`;
    const password = 'supersecretpwd';

    // 1) Solicitud pública. Responde 202 sin revelar nada (RF-REG-005).
    const requested = await api.post('/api/v1/public/tenant-registrations', {
      headers: { 'X-Forwarded-For': `203.0.113.${Math.floor(Math.random() * 254) + 1}` },
      data: {
        companyName: `Empresa ${suffix}`,
        timezone: 'Europe/Madrid',
        firstName: 'Olivia',
        lastName: 'Registro',
        email,
        password,
        acceptTerms: true
      }
    });
    expect(requested.status()).toBe(202);

    // 2) El correo de verificación llega por el outbox, no de forma síncrona.
    const mail = await waitForEmail(email);
    expect(mail.subject).toMatch(/verifica|confirma/i);
    const token = /token=([A-Za-z0-9._~-]+)/.exec(mail.body)?.[1];
    expect(token, `No se encontró token en el correo: ${mail.body}`).toBeTruthy();

    // 3) Verificación del correo con el token recibido.
    const verified = await api.post('/api/v1/public/tenant-registrations/verify-email', {
      data: { token }
    });
    expect(verified.ok()).toBeTruthy();

    // 4) Plataforma revisa y aprueba: hasta aquí no existía ningún tenant.
    const platform = platformCredentials();
    const platformToken = await login(api, platform.email, platform.password);

    const pending = await api.get('/api/v1/platform/registrations?page=0&size=50', {
      headers: bearer(platformToken)
    });
    expect(pending.ok()).toBeTruthy();
    const registration = (await pending.json()).content.find(
      (row: { email: string }) => row.email === email
    );
    expect(registration, 'La solicitud verificada no aparece en la bandeja de plataforma').toBeTruthy();

    const approved = await api.post(`/api/v1/platform/registrations/${registration.id}/approve`, {
      headers: bearer(platformToken)
    });
    expect(approved.ok()).toBeTruthy();

    // 5) Aprobar crea el tenant en PENDING, no operativo: activarlo es una
    // decisión distinta y explícita de plataforma (RF-TEN-005, ADR-0016). Sin
    // este paso el propietario recibe TENANT_INACTIVE al intentar entrar.
    const tenants = await api.get('/api/v1/platform/tenants?page=0&size=100&status=PENDING', {
      headers: bearer(platformToken)
    });
    expect(tenants.ok()).toBeTruthy();
    const tenant = (await tenants.json()).content.find(
      (row: { name: string }) => row.name === `Empresa ${suffix}`
    );
    expect(tenant, 'El tenant aprobado no aparece como PENDING en plataforma').toBeTruthy();

    const activated = await api.post(`/api/v1/platform/tenants/${tenant.id}/activate`, {
      headers: bearer(platformToken)
    });
    expect(activated.ok()).toBeTruthy();

    // 6) Ahora sí: el propietario puede autenticarse.
    const ownerToken = await login(api, email, password);
    expect(ownerToken).toBeTruthy();

    // 7) El tenant nace con su catálogo de tipos de ausencia (RF-ABS-001). El
    // alta pública emite su evento con el tenant de plataforma en el envelope y
    // el tenant real solo en el payload: sembrar sobre el envelope dejaba a
    // toda empresa registrada por esta vía sin poder solicitar ausencias, y no
    // se detectaba porque el resto de E2E crea el tenant desde plataforma.
    // Llega por el outbox, así que hay una ventana de consistencia eventual.
    await expect
      .poll(
        async () => {
          const response = await api.get('/api/v1/app/absence-types', { headers: bearer(ownerToken) });
          return response.ok() ? (await response.json()).length : 0;
        },
        { timeout: 40_000, intervals: [1_000] }
      )
      .toBe(5);
  });

  test('una solicitud repetida no revela que el correo ya existe', async () => {
    const suffix = unique('e2e-enum');
    const email = `dup-${suffix}@acme.test`;
    const payload = {
      companyName: `Empresa ${suffix}`,
      timezone: 'Europe/Madrid',
      firstName: 'Olivia',
      lastName: 'Registro',
      email,
      password: 'supersecretpwd',
      acceptTerms: true
    };

    const forwarded = { 'X-Forwarded-For': `203.0.113.${Math.floor(Math.random() * 254) + 1}` };
    const first = await api.post('/api/v1/public/tenant-registrations', { headers: forwarded, data: payload });
    const second = await api.post('/api/v1/public/tenant-registrations', { headers: forwarded, data: payload });

    expect(first.status()).toBe(202);
    expect(second.status()).toBe(second.status());
    expect(await second.text()).toBe(await first.text());
  });
});
