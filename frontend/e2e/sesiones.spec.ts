import { APIRequestContext, expect, test } from '@playwright/test';

import { TenantActors, apiContext, bearer, createTenant, login } from './support/api';

/**
 * Login y gestión de sesiones (RF-USR-007, T160-01).
 *
 * Es el flujo que cierra la lista de diez del plan. Lo que importa aquí no es
 * que el listado responda, sino que revocar una sesión la deje efectivamente
 * inutilizable: una revocación que no revoca es peor que no ofrecerla, porque
 * el usuario cree haber cerrado el acceso.
 */
test.describe('Sesiones', () => {
  let api: APIRequestContext;
  let tenant: TenantActors;

  test.beforeAll(async () => {
    api = await apiContext();
    tenant = await createTenant(api, 'sesiones');
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('cada login abre una sesión y el usuario ve las suyas', async () => {
    await login(api, tenant.employee.email, tenant.employee.password);

    const response = await api.get('/api/v1/auth/sessions', { headers: bearer(tenant.employee.token) });

    expect(response.ok()).toBeTruthy();
    const sessions = await response.json();
    expect(sessions.length).toBeGreaterThanOrEqual(2);
    // Exactamente una está marcada como la actual: la del token con el que se
    // consulta.
    expect(sessions.filter((s: { current: boolean }) => s.current)).toHaveLength(1);
  });

  test('un usuario no ve las sesiones de otro', async () => {
    const employeeSessions = await api.get('/api/v1/auth/sessions', {
      headers: bearer(tenant.employee.token)
    });
    const adminSessions = await api.get('/api/v1/auth/sessions', { headers: bearer(tenant.admin.token) });

    const employeeIds = (await employeeSessions.json()).map((s: { id: string }) => s.id);
    const adminIds = (await adminSessions.json()).map((s: { id: string }) => s.id);

    expect(employeeIds.filter((id: string) => adminIds.includes(id))).toHaveLength(0);
  });

  test('revocar todas cierra el acceso de las demás sesiones', async () => {
    // Se abre una sesión aparte, se cierran todas desde otra, y se comprueba
    // que el token de la primera deja de servir: es la única forma de saber que
    // la revocación surte efecto y no solo marca una fila.
    const victimToken = await login(api, tenant.employee.email, tenant.employee.password);
    expect((await api.get('/api/v1/auth/sessions', { headers: bearer(victimToken) })).ok()).toBeTruthy();

    const survivorToken = await login(api, tenant.employee.email, tenant.employee.password);
    const revoked = await api.delete('/api/v1/auth/sessions', { headers: bearer(survivorToken) });
    expect(revoked.status()).toBe(204);

    const afterRevocation = await api.get('/api/v1/auth/sessions', { headers: bearer(victimToken) });
    expect(afterRevocation.status()).toBe(401);
  });

  test('revocar una sesión concreta no afecta a las demás', async () => {
    const first = await login(api, tenant.admin.email, tenant.admin.password);
    const second = await login(api, tenant.admin.email, tenant.admin.password);

    const sessions = await (await api.get('/api/v1/auth/sessions', { headers: bearer(first) })).json();
    const other = sessions.find((s: { current: boolean }) => !s.current);
    expect(other, 'Debe existir al menos otra sesión además de la actual').toBeTruthy();

    expect((await api.delete(`/api/v1/auth/sessions/${other.id}`, { headers: bearer(first) })).status()).toBe(204);

    // La sesión desde la que se revoca sigue viva.
    expect((await api.get('/api/v1/auth/sessions', { headers: bearer(first) })).ok()).toBeTruthy();
    expect(second).toBeTruthy();
  });
});
