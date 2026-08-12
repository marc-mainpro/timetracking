import { APIRequestContext, expect, test } from '@playwright/test';

import { apiContext, createTenant, unique, waitForEmail } from './support/api';

/** IP de documentación distinta por llamada: /password/** admite 5 por 15 min (RS-007). */
function forwarded(): Record<string, string> {
  return { 'X-Forwarded-For': `203.0.113.${Math.floor(Math.random() * 254) + 1}` };
}

/**
 * Recuperación de contraseña de extremo a extremo (RF-USR-006, T160-01).
 *
 * El valor de esta prueba está en el tramo que ninguna prueba de integración
 * cubre: que el token que viaja por el outbox hasta el correo sea el mismo que
 * acepta el endpoint de restablecimiento, y que la contraseña resultante sirva
 * para entrar. El enlace se compone con `reset-url-template`, así que un fallo
 * de configuración de esa plantilla también se ve aquí.
 */
test.describe('Recuperación de contraseña', () => {
  let api: APIRequestContext;

  test.beforeAll(async () => {
    api = await apiContext();
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('solicitud, correo, restablecimiento y acceso con la contraseña nueva', async () => {
    const actors = await createTenant(api, 'e2e-reset');
    const { email, password: oldPassword } = actors.employee;
    const newPassword = 'contrasenarecuperada';

    // 1) Solicitud. Responde 202 sin revelar si la cuenta existe (RS-007).
    const requested = await api.post('/api/v1/auth/password/forgot', {
      headers: forwarded(),
      data: { email }
    });
    expect(requested.status()).toBe(202);

    // 2) El correo llega por el outbox, no de forma síncrona.
    const mail = await waitForEmail(email);
    expect(mail.subject).toMatch(/contrasena|contraseña/i);
    expect(mail.body, 'El enlace del correo debe apuntar a la pantalla de restablecimiento').toContain(
      '/restablecer-contrasena?token='
    );
    const token = /token=([A-Za-z0-9._~-]+)/.exec(mail.body)?.[1];
    expect(token, `No se encontró token en el correo: ${mail.body}`).toBeTruthy();

    // 3) Restablecimiento con el token recibido.
    const reset = await api.post('/api/v1/auth/password/reset', {
      headers: forwarded(),
      data: { token, newPassword }
    });
    expect(reset.status()).toBe(204);

    // 4) La contraseña nueva entra y la anterior deja de valer.
    const withNew = await api.post('/api/v1/auth/login', {
      headers: forwarded(),
      data: { email, password: newPassword }
    });
    expect(withNew.ok()).toBeTruthy();

    const withOld = await api.post('/api/v1/auth/login', {
      headers: forwarded(),
      data: { email, password: oldPassword }
    });
    expect(withOld.ok()).toBeFalsy();

    // 5) El token es de un solo uso: el mismo enlace ya no sirve.
    const replay = await api.post('/api/v1/auth/password/reset', {
      headers: forwarded(),
      data: { token, newPassword: 'otracontrasena123' }
    });
    expect(replay.status()).toBe(401);
    expect((await replay.json()).errorCode).toBe('INVALID_PASSWORD_RESET_TOKEN');
  });

  test('una cuenta inexistente recibe la misma respuesta que una real', async () => {
    const unknown = `nadie-${unique('e2e-enum-reset')}@acme.test`;

    const first = await api.post('/api/v1/auth/password/forgot', {
      headers: forwarded(),
      data: { email: unknown }
    });

    expect(first.status()).toBe(202);
    expect(await first.text()).toContain('Si la cuenta existe');
  });
});
