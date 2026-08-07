import { APIRequestContext, expect, test } from '@playwright/test';

import { TenantActors, apiContext, bearer, createTenant } from './support/api';

/**
 * Ausencias y la notificación que generan (RF-ABS-002..005, RF-NOT-001,
 * RF-NOT-003, RF-NOT-004, T160-01).
 *
 * Van juntas a propósito: la notificación es la consecuencia observable de
 * resolver una ausencia, y su recorrido atraviesa el Outbox. Probarlas por
 * separado dejaría sin verificar justamente la unión.
 */
test.describe('Ausencias y notificaciones', () => {
  let api: APIRequestContext;
  let tenant: TenantActors;
  let absenceTypeId: string;

  test.beforeAll(async () => {
    api = await apiContext();
    tenant = await createTenant(api, 'ausencia');

    // El catálogo de tipos se siembra al crear el tenant, pero por el Outbox:
    // hay una ventana de consistencia eventual de unos segundos entre que el
    // tenant existe y sus tipos están disponibles.
    await expect
      .poll(
        async () => {
          const response = await api.get('/api/v1/app/absence-types', {
            headers: bearer(tenant.employee.token)
          });
          return (await response.json()).length;
        },
        { timeout: 40_000, intervals: [1_000] }
      )
      .toBeGreaterThan(0);

    const types = await api.get('/api/v1/app/absence-types', { headers: bearer(tenant.employee.token) });
    absenceTypeId = (await types.json())[0].id;
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  function futureRange(offsetDays: number): { startDate: string; endDate: string } {
    const start = new Date(Date.now() + offsetDays * 24 * 60 * 60 * 1000);
    const end = new Date(start.getTime() + 24 * 60 * 60 * 1000);
    return { startDate: start.toISOString().slice(0, 10), endDate: end.toISOString().slice(0, 10) };
  }

  async function requestAbsence(offsetDays: number): Promise<string> {
    const created = await api.post('/api/v1/app/absences', {
      headers: bearer(tenant.employee.token),
      data: { absenceTypeId, ...futureRange(offsetDays), reason: 'Asuntos propios' }
    });
    // Nota: este alta responde 200, mientras que el resto de creaciones de la
    // API responden 201. Es una inconsistencia del contrato, no un fallo.
    expect(created.ok()).toBeTruthy();
    return (await created.json()).id;
  }

  test('el empleado solicita, el administrador aprueba y se notifica al empleado', async () => {
    const absenceId = await requestAbsence(30);

    // El listado de administración se filtra por rango de fechas, no paginado.
    const range = futureRange(30);
    const pending = await api.get(
      `/api/v1/admin/absences?from=${range.startDate}&to=${range.endDate}`,
      { headers: bearer(tenant.admin.token) }
    );
    expect(pending.ok()).toBeTruthy();
    expect(await pending.text()).toContain(absenceId);

    const approved = await api.post(`/api/v1/admin/absences/${absenceId}/approve`, {
      headers: bearer(tenant.admin.token),
      data: { resolutionComment: 'Aprobado' }
    });
    expect(approved.ok()).toBeTruthy();

    // La notificación no es síncrona: el evento viaja por el Outbox y lo
    // publica una tarea programada, así que se espera a que aparezca.
    await expect
      .poll(
        async () => {
          const response = await api.get('/api/v1/notifications?page=0&size=50', {
            headers: bearer(tenant.employee.token)
          });
          const body = await response.json();
          return body.content.filter((n: { type: string }) => n.type === 'ABSENCE_APPROVED').length;
        },
        { timeout: 40_000, intervals: [1_000] }
      )
      .toBeGreaterThan(0);

    const unread = await api.get('/api/v1/notifications/unread-count', {
      headers: bearer(tenant.employee.token)
    });
    expect((await unread.json()).unread).toBeGreaterThan(0);
  });

  test('marcar como leída baja el contador de no leídas', async () => {
    const list = await api.get('/api/v1/notifications?page=0&size=50', {
      headers: bearer(tenant.employee.token)
    });
    const notification = (await list.json()).content.find((n: { read: boolean }) => !n.read);
    expect(notification, 'Debe existir alguna notificación sin leer del caso anterior').toBeTruthy();

    const before = (await (await api.get('/api/v1/notifications/unread-count', {
      headers: bearer(tenant.employee.token)
    })).json()).unread;

    const marked = await api.post(`/api/v1/notifications/${notification.id}/read`, {
      headers: bearer(tenant.employee.token)
    });
    expect(marked.status()).toBe(204);

    const after = (await (await api.get('/api/v1/notifications/unread-count', {
      headers: bearer(tenant.employee.token)
    })).json()).unread;
    expect(after).toBe(before - 1);
  });

  test('un rechazo también avisa al empleado', async () => {
    const absenceId = await requestAbsence(60);

    const rejected = await api.post(`/api/v1/admin/absences/${absenceId}/reject`, {
      headers: bearer(tenant.admin.token),
      data: { resolutionComment: 'No hay cobertura esos días' }
    });
    expect(rejected.ok()).toBeTruthy();

    await expect
      .poll(
        async () => {
          const response = await api.get('/api/v1/notifications?page=0&size=50', {
            headers: bearer(tenant.employee.token)
          });
          const body = await response.json();
          return body.content.filter((n: { type: string }) => n.type === 'ABSENCE_REJECTED').length;
        },
        { timeout: 40_000, intervals: [1_000] }
      )
      .toBeGreaterThan(0);
  });

  test('el empleado no ve las notificaciones de otro tenant', async () => {
    const other = await createTenant(api, 'ausencia-otro');

    const response = await api.get('/api/v1/notifications?page=0&size=50', {
      headers: bearer(other.employee.token)
    });

    expect(response.ok()).toBeTruthy();
    expect((await response.json()).content).toHaveLength(0);
  });
});
