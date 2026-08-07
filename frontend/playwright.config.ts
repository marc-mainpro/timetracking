import { defineConfig, devices } from '@playwright/test';

/**
 * E2E de navegador (T160-01).
 *
 * Se ejecutan contra la pila completa levantada con Docker Compose —frontend,
 * backend, PostgreSQL y mailpit—, no contra `ng serve` con el backend simulado:
 * el valor de estas pruebas está justamente en atravesar todas las capas, y un
 * backend simulado no detectaría una migración rota ni un fallo de aislamiento
 * entre tenants.
 *
 * Requisitos previos (ver `npm run e2e`):
 *   - `docker compose up -d --build`
 *   - `PUBLIC_REGISTRATION_ENABLED=true` para los flujos de alta pública
 *   - `PLATFORM_ADMIN_EMAIL` / `PLATFORM_ADMIN_PASSWORD` para los de plataforma
 */
export default defineConfig({
  testDir: './e2e',
  // Un solo worker: las pruebas comparten la misma base de datos y varias
  // dependen de contar filas o de listados completos, así que ejecutarlas en
  // paralelo las volvería dependientes del orden.
  workers: 1,
  fullyParallel: false,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  // Reintento único en CI: el arranque del stack y el polling del outbox
  // introducen latencias que no son fallos del producto.
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]
});
