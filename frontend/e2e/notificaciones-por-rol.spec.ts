import { APIRequestContext, expect, test } from '@playwright/test';

import {
  TenantActors,
  apiContext,
  bearer,
  createTenant,
  login,
  platformCredentials,
  unique,
  waitForEmail
} from './support/api';

interface Notification {
  id: string;
  type: string;
  actionPath: string | null;
  body: string;
}

/**
 * Avisos dirigidos a un rol y no a una persona (RF-NOT-003, RF-NOT-007,
 * T170-11, ADR-0018).
 *
 * Es el recorrido que ninguna prueba de integración cubre entera: el hecho de
 * negocio ocurre en un módulo, viaja por el Outbox, el consumidor pregunta a
 * `identity` quién tiene el rol y crea una notificación por cada uno. Lo que se
 * verifica aquí es esa unión, no el fan-out en sí.
 */
test.describe('Notificaciones por rol', () => {
  let api: APIRequestContext;

  test.beforeAll(async () => {
    api = await apiContext();
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  /**
   * Las notificaciones no son síncronas: el evento viaja por el Outbox y lo
   * publica una tarea programada, así que hay que esperar a que aparezcan.
   */
  async function waitForNotification(token: string, type: string): Promise<Notification> {
    let found: Notification | undefined;
    await expect
      .poll(
        async () => {
          const response = await api.get('/api/v1/notifications?page=0&size=50', {
            headers: bearer(token)
          });
          const body = await response.json();
          found = body.content.find((n: Notification) => n.type === type);
          return found ? 1 : 0;
        },
        { timeout: 40_000, intervals: [1_000] }
      )
      .toBe(1);
    return found as Notification;
  }

  test('un empleado solicita una ausencia y su administrador recibe el aviso', async () => {
    const tenant: TenantActors = await createTenant(api, 'notif-rol');

    // El catálogo de tipos se siembra por el Outbox: hay una ventana de
    // consistencia eventual entre que el tenant existe y sus tipos están.
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
    const types = await api.get('/api/v1/app/absence-types', {
      headers: bearer(tenant.employee.token)
    });
    const absenceTypeId = (await types.json())[0].id;

    const start = new Date(Date.now() + 45 * 24 * 60 * 60 * 1000);
    const end = new Date(start.getTime() + 24 * 60 * 60 * 1000);
    const requested = await api.post('/api/v1/app/absences', {
      headers: bearer(tenant.employee.token),
      data: {
        absenceTypeId,
        startDate: start.toISOString().slice(0, 10),
        endDate: end.toISOString().slice(0, 10),
        reason: 'Asuntos propios'
      }
    });
    expect(requested.ok()).toBeTruthy();

    const notification = await waitForNotification(tenant.admin.token, 'ABSENCE_REQUESTED');
    expect(notification.actionPath).toBe('/admin/absences');

    // Al solicitante no se le avisa por esta vía: ya sabe que ha solicitado.
    const employeeInbox = await api.get('/api/v1/notifications?page=0&size=50', {
      headers: bearer(tenant.employee.token)
    });
    const employeeTypes = (await employeeInbox.json()).content.map((n: Notification) => n.type);
    expect(employeeTypes).not.toContain('ABSENCE_REQUESTED');
  });

  test('una solicitud de alta verificada avisa al administrador de plataforma', async () => {
    const platform = platformCredentials();
    const platformToken = await login(api, platform.email, platform.password);

    const suffix = unique('e2e-notif-alta');
    const email = `owner-${suffix}@acme.test`;
    const companyName = `Empresa ${suffix}`;

    const requested = await api.post('/api/v1/public/tenant-registrations', {
      headers: { 'X-Forwarded-For': `203.0.113.${Math.floor(Math.random() * 254) + 1}` },
      data: {
        companyName,
        timezone: 'Europe/Madrid',
        firstName: 'Olivia',
        lastName: 'Registro',
        email,
        password: 'supersecretpwd',
        acceptTerms: true
      }
    });
    expect(requested.status()).toBe(202);

    // Sin verificar el correo no debe haber aviso: no se anuncian altas que
    // quizá nunca lleguen a confirmarse.
    const beforeVerification = await api.get('/api/v1/notifications?page=0&size=50', {
      headers: bearer(platformToken)
    });
    const bodies = (await beforeVerification.json()).content
      .filter((n: Notification) => n.type === 'REGISTRATION_PENDING_REVIEW')
      .map((n: Notification) => n.body);
    expect(bodies.join('\n')).not.toContain(companyName);

    const mail = await waitForEmail(email);
    const token = /token=([A-Za-z0-9._~-]+)/.exec(mail.body)?.[1];
    expect(token, `No se encontró token en el correo: ${mail.body}`).toBeTruthy();

    const verified = await api.post('/api/v1/public/tenant-registrations/verify-email', {
      data: { token }
    });
    expect(verified.ok()).toBeTruthy();

    await expect
      .poll(
        async () => {
          const response = await api.get('/api/v1/notifications?page=0&size=50', {
            headers: bearer(platformToken)
          });
          const body = await response.json();
          return body.content.filter(
            (n: Notification) =>
              n.type === 'REGISTRATION_PENDING_REVIEW' && n.body.includes(companyName)
          ).length;
        },
        { timeout: 40_000, intervals: [1_000] }
      )
      .toBeGreaterThan(0);

    const inbox = await api.get('/api/v1/notifications?page=0&size=50', {
      headers: bearer(platformToken)
    });
    const notification = (await inbox.json()).content.find(
      (n: Notification) => n.type === 'REGISTRATION_PENDING_REVIEW' && n.body.includes(companyName)
    );
    expect(notification.actionPath).toBe('/platform/registrations');
  });
});
