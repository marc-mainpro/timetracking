import { APIRequestContext, expect, test } from '@playwright/test';

import { TenantActors, apiContext, bearer, createTenant, login } from './support/api';

/**
 * Aislamiento entre tenants y por rol (RNF-006, RS-010, RS-011, RT-003, RT-004).
 *
 * Es la propiedad que más caro cuesta perder y la que menos ruido hace al
 * romperse: una fuga no falla, simplemente devuelve datos de más.
 */
test.describe('Aislamiento multitenant', () => {
  let api: APIRequestContext;
  let tenantA: TenantActors;
  let tenantB: TenantActors;

  test.beforeAll(async () => {
    api = await apiContext();
    tenantA = await createTenant(api, 'aisl-a');
    tenantB = await createTenant(api, 'aisl-b');
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('un tenant no ve los empleados de otro', async () => {
    const response = await api.get('/api/v1/employees?page=0&size=100', {
      headers: bearer(tenantA.admin.token)
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.text();
    expect(body).toContain(tenantA.employee.email);
    expect(body).not.toContain(tenantB.employee.email);
  });

  test('una jornada de otro tenant responde 404, no 403', async () => {
    // 403 confirmaría que ese identificador existe: la respuesta correcta es
    // indistinguible de la de un recurso inexistente.
    const started = await api.post('/api/v1/workdays/start', { headers: bearer(tenantB.employee.token) });
    expect(started.status()).toBe(201);
    const foreignWorkdayId = (await started.json()).id;
    await api.post('/api/v1/workdays/current/end', { headers: bearer(tenantB.employee.token) });

    const response = await api.get(`/api/v1/workdays/${foreignWorkdayId}`, {
      headers: bearer(tenantA.employee.token)
    });

    expect(response.status()).toBe(404);
  });

  test('un empleado no accede a la administración de su tenant', async () => {
    const response = await api.get('/api/v1/employees?page=0&size=20', {
      headers: bearer(tenantA.employee.token)
    });

    expect(response.status()).toBe(403);
  });

  test('un administrador de tenant no accede a la API de plataforma', async () => {
    const response = await api.get('/api/v1/platform/tenants', { headers: bearer(tenantA.admin.token) });

    expect(response.status()).toBe(403);
  });

  test('un tenant suspendido deja de operar y vuelve al reactivarlo', async () => {
    const platform = await login(
      api,
      process.env['PLATFORM_ADMIN_EMAIL'] as string,
      process.env['PLATFORM_ADMIN_PASSWORD'] as string
    );

    const suspended = await api.post(`/api/v1/platform/tenants/${tenantB.tenantId}/suspend`, {
      headers: bearer(platform),
      data: { reason: 'Prueba E2E de suspensión' }
    });
    expect(suspended.ok()).toBeTruthy();

    const blocked = await api.post('/api/v1/auth/login', {
      headers: { 'X-Forwarded-For': `198.51.100.${Math.floor(Math.random() * 254) + 1}` },
      data: { email: tenantB.employee.email, password: tenantB.employee.password }
    });
    expect(blocked.status()).toBe(401);
    expect((await blocked.json()).errorCode).toBe('TENANT_INACTIVE');

    const reactivated = await api.post(`/api/v1/platform/tenants/${tenantB.tenantId}/reactivate`, {
      headers: bearer(platform)
    });
    expect(reactivated.ok()).toBeTruthy();

    expect(await login(api, tenantB.employee.email, tenantB.employee.password)).toBeTruthy();
  });
});
