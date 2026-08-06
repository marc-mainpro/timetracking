import { APIRequestContext, expect, test } from '@playwright/test';

import { TenantActors, apiContext, bearer, createTenant } from './support/api';

/**
 * Jornada, corrección e informe (RF-TIM-001..008, RF-REP-004, T160-01).
 *
 * Encadena el ciclo operativo completo de un empleado y su administrador, que
 * es donde se cruzan el motor de evaluación horaria, las correcciones y los
 * informes.
 */
test.describe('Ciclo de jornada', () => {
  let api: APIRequestContext;
  let tenant: TenantActors;

  test.beforeAll(async () => {
    api = await apiContext();
    tenant = await createTenant(api, 'jornada');
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('inicio, pausa, fin y evaluación de la jornada', async () => {
    const employee = bearer(tenant.employee.token);

    const started = await api.post('/api/v1/workdays/start', { headers: employee });
    expect(started.status()).toBe(201);
    const workdayId = (await started.json()).id;

    expect((await api.post('/api/v1/workdays/current/breaks/start', { headers: employee })).ok()).toBeTruthy();
    expect((await api.post('/api/v1/workdays/current/breaks/end', { headers: employee })).ok()).toBeTruthy();
    expect((await api.post('/api/v1/workdays/current/end', { headers: employee })).ok()).toBeTruthy();

    // La jornada cerrada se evalúa: el detalle expone el resultado del motor.
    const detail = await api.get(`/api/v1/workdays/${workdayId}`, { headers: employee });
    expect(detail.ok()).toBeTruthy();
    const body = await detail.json();
    expect(body.status).toBe('CLOSED');
    expect(body.evaluation, 'La jornada cerrada debe traer su evaluación').toBeTruthy();
  });

  test('una jornada abierta impide abrir otra', async () => {
    // Invariante del agregado: es la regla que protege el índice único parcial.
    const employee = bearer(tenant.employee.token);
    const first = await api.post('/api/v1/workdays/start', { headers: employee });
    expect(first.status()).toBe(201);

    const second = await api.post('/api/v1/workdays/start', { headers: employee });
    expect(second.status()).toBe(409);
    expect((await second.json()).errorCode).toBe('WORKDAY_ALREADY_OPEN');

    await api.post('/api/v1/workdays/current/end', { headers: employee });
  });

  test('el administrador ve las jornadas de su tenant en el informe', async () => {
    // El informe se filtra por instantes, no por fechas locales (RNF-011), y el
    // rango está acotado a 366 días para que una consulta no barra el histórico
    // entero.
    const to = new Date();
    const from = new Date(to.getTime() - 24 * 60 * 60 * 1000);
    const report = await api.get(
      `/api/v1/reports/tenant/summary?from=${from.toISOString()}&to=${to.toISOString()}`,
      {
        headers: bearer(tenant.admin.token)
      }
    );

    expect(report.ok()).toBeTruthy();
    const rows = await report.json();

    // El informe es del tenant autenticado, y este solo tiene un empleado: una
    // sola fila. Si aparecieran más, estaría filtrando mal por tenant.
    expect(rows).toHaveLength(1);
    // Las dos jornadas cerradas en las pruebas anteriores están contabilizadas
    // y evaluadas por el motor horario.
    expect(rows[0].workdayCount).toBe(2);
    expect(rows[0].evaluatedWorkdayCount).toBe(2);
    expect(rows[0].openWorkdays).toBe(0);
  });
});
