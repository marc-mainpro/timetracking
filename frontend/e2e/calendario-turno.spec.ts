import { APIRequestContext, expect, test } from '@playwright/test';

import { TenantActors, apiContext, bearer, createTenant, unique } from './support/api';

/**
 * Calendario laboral y turnos (RF-CAL-001..007, RF-SHF-001..006, T160-01).
 *
 * Comprueba que la planificación llega hasta la evaluación de la jornada: el
 * calendario define lo esperado por ámbito y el turno, más específico, lo
 * sustituye.
 */
test.describe('Calendario y turnos', () => {
  let api: APIRequestContext;
  let tenant: TenantActors;

  test.beforeAll(async () => {
    api = await apiContext();
    tenant = await createTenant(api, 'planificacion');
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('crear calendario, asignarlo y resolver el efectivo del empleado', async () => {
    const admin = bearer(tenant.admin.token);

    const created = await api.post('/api/v1/admin/calendars', {
      headers: admin,
      data: {
        name: `Calendario ${unique('cal')}`,
        timezone: 'Europe/Madrid',
        validFrom: '2020-01-01',
        dayRules: [
          { dayOfWeek: 'MONDAY', working: true, expectedMinutes: 480 },
          { dayOfWeek: 'SATURDAY', working: false, expectedMinutes: 0 },
          { dayOfWeek: 'SUNDAY', working: false, expectedMinutes: 0 }
        ],
        holidays: [{ date: '2026-01-06', name: 'Reyes' }],
        specialDays: [{ date: '2026-12-24', name: 'Nochebuena', expectedMinutes: 240 }]
      }
    });
    expect(created.ok()).toBeTruthy();
    const calendarId = (await created.json()).id;

    const assigned = await api.post('/api/v1/admin/calendar-assignments', {
      headers: admin,
      data: { calendarId, scope: 'EMPLOYEE', targetId: tenant.employeeId }
    });
    expect(assigned.ok()).toBeTruthy();

    // El calendario efectivo del empleado es el que se le asignó: la resolución
    // por ámbito devuelve el más específico.
    const effective = await api.get(
      `/api/v1/admin/calendar-assignments/effective?employeeId=${tenant.employeeId}&date=2026-01-05`,
      { headers: admin }
    );
    expect(effective.ok()).toBeTruthy();
    expect(JSON.stringify(await effective.json())).toContain(calendarId);
  });

  test('un turno nocturno descuenta la pausa prevista y lo ve el empleado', async () => {
    const admin = bearer(tenant.admin.token);

    const template = await api.post('/api/v1/admin/shifts/templates', {
      headers: admin,
      // Cruza medianoche: su duración no es fin menos inicio.
      data: { name: `Nocturno ${unique('t')}`, startTime: '22:00:00', endTime: '06:00:00', plannedBreakMinutes: 30 }
    });
    expect(template.ok()).toBeTruthy();
    const shiftTemplateId = (await template.json()).id;

    const assigned = await api.post('/api/v1/admin/shifts/assignments', {
      headers: admin,
      data: { employeeId: tenant.employeeId, shiftTemplateId, validFrom: '2020-01-01' }
    });
    expect(assigned.ok()).toBeTruthy();

    const own = await api.get(`/api/v1/app/shifts?date=${new Date().toISOString().slice(0, 10)}`, {
      headers: bearer(tenant.employee.token)
    });
    expect(own.ok()).toBeTruthy();
    const shifts = await own.json();
    expect(shifts).toHaveLength(1);
    expect(shifts[0].crossesMidnight).toBe(true);
    expect(shifts[0].plannedDuration).toBe('PT8H');
  });

  test('el turno asignado pasa a ser el tiempo previsto de la jornada', async () => {
    // T90-06: el turno prevalece sobre el calendario por ser la planificación
    // más específica, y el previsto descuenta la pausa (8 h − 30 min).
    const employee = bearer(tenant.employee.token);
    const started = await api.post('/api/v1/workdays/start', { headers: employee });
    expect(started.status()).toBe(201);
    const workdayId = (await started.json()).id;
    expect((await api.post('/api/v1/workdays/current/end', { headers: employee })).ok()).toBeTruthy();

    const detail = await api.get(`/api/v1/workdays/${workdayId}`, { headers: employee });
    const body = await detail.json();
    expect(body.evaluation.expectedDuration).toBe('PT7H30M');
  });
});
