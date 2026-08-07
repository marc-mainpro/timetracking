import { APIRequestContext, expect, test } from '@playwright/test';

import { TenantActors, apiContext, bearer, createTenant } from './support/api';

/**
 * Solicitud y resolución de corrección de jornada (RF-TIM-006..008, T160-01).
 *
 * Cierra el bucle completo: el empleado pide, el administrador resuelve y la
 * jornada se reevalúa con los nuevos tiempos.
 */
test.describe('Corrección de jornada', () => {
  let api: APIRequestContext;
  let tenant: TenantActors;

  test.beforeAll(async () => {
    api = await apiContext();
    tenant = await createTenant(api, 'correccion');
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  /** Cierra una jornada y devuelve su identificador. */
  async function closedWorkday(): Promise<string> {
    const employee = bearer(tenant.employee.token);
    const started = await api.post('/api/v1/workdays/start', { headers: employee });
    expect(started.status()).toBe(201);
    const id = (await started.json()).id;
    expect((await api.post('/api/v1/workdays/current/end', { headers: employee })).ok()).toBeTruthy();
    return id;
  }

  function proposedChanges(): Record<string, unknown> {
    const end = new Date();
    const start = new Date(end.getTime() - 8 * 60 * 60 * 1000);
    const breakStart = new Date(start.getTime() + 4 * 60 * 60 * 1000);
    const breakEnd = new Date(breakStart.getTime() + 30 * 60 * 1000);
    return {
      startedAt: start.toISOString(),
      endedAt: end.toISOString(),
      breaks: [{ startedAt: breakStart.toISOString(), endedAt: breakEnd.toISOString() }]
    };
  }

  test('el empleado solicita y el administrador aprueba, y la jornada se reevalúa', async () => {
    const workdayId = await closedWorkday();

    const requested = await api.post(`/api/v1/workdays/${workdayId}/corrections`, {
      headers: bearer(tenant.employee.token),
      data: { reason: 'Olvidé fichar la salida', proposedChanges: proposedChanges() }
    });
    expect(requested.status()).toBe(201);
    const correctionId = (await requested.json()).id;

    const approved = await api.post(`/api/v1/corrections/${correctionId}/approve`, {
      headers: bearer(tenant.admin.token),
      data: { resolutionComment: 'Verificado con el responsable' }
    });
    expect(approved.ok()).toBeTruthy();

    // Aprobar aplica los cambios y vuelve a evaluar la jornada: ocho horas
    // menos media de pausa son siete y media de trabajo efectivo.
    const detail = await api.get(`/api/v1/workdays/${workdayId}`, { headers: bearer(tenant.employee.token) });
    expect(detail.ok()).toBeTruthy();
    const body = await detail.json();
    expect(body.evaluation).toBeTruthy();
    expect(body.evaluation.workedDuration).toBe('PT7H30M');
  });

  test('una corrección rechazada no altera la jornada', async () => {
    const workdayId = await closedWorkday();
    const before = await (await api.get(`/api/v1/workdays/${workdayId}`, {
      headers: bearer(tenant.employee.token)
    })).json();

    const requested = await api.post(`/api/v1/workdays/${workdayId}/corrections`, {
      headers: bearer(tenant.employee.token),
      data: { reason: 'Prueba de rechazo', proposedChanges: proposedChanges() }
    });
    const correctionId = (await requested.json()).id;

    const rejected = await api.post(`/api/v1/corrections/${correctionId}/reject`, {
      headers: bearer(tenant.admin.token),
      data: { resolutionComment: 'No procede' }
    });
    expect(rejected.ok()).toBeTruthy();

    const after = await (await api.get(`/api/v1/workdays/${workdayId}`, {
      headers: bearer(tenant.employee.token)
    })).json();
    expect(after.startedAt).toBe(before.startedAt);
    expect(after.endedAt).toBe(before.endedAt);
  });

  test('una corrección ya resuelta no se puede resolver otra vez', async () => {
    // Doble aprobación (RT-006): la segunda debe chocar con la invariante del
    // agregado, no aplicarse dos veces.
    const workdayId = await closedWorkday();
    const requested = await api.post(`/api/v1/workdays/${workdayId}/corrections`, {
      headers: bearer(tenant.employee.token),
      data: { reason: 'Doble resolución', proposedChanges: proposedChanges() }
    });
    const correctionId = (await requested.json()).id;

    expect(
      (await api.post(`/api/v1/corrections/${correctionId}/approve`, {
        headers: bearer(tenant.admin.token),
        data: {}
      })).ok()
    ).toBeTruthy();

    const second = await api.post(`/api/v1/corrections/${correctionId}/approve`, {
      headers: bearer(tenant.admin.token),
      data: {}
    });
    expect(second.status()).toBe(409);
    expect((await second.json()).errorCode).toBe('CORRECTION_ALREADY_RESOLVED');
  });
});
