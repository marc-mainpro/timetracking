import { Injectable, computed, inject, signal } from '@angular/core';

import { AuthService } from './auth.service';
import { Role, VIEW_HOME_ROUTE, VIEW_PRIORITY, VIEW_ROLE, ViewMode } from '../models/role';

/**
 * Vista elegida, junto al usuario que la eligió.
 *
 * <p>Va ligada al `sub` del JWT porque el navegador es compartido: sin eso,
 * entrar con otra cuenta heredaría la vista de la anterior. Guardar el usuario
 * también evita tener que acordarse de limpiar al cerrar sesión —un valor de
 * otro usuario simplemente se ignora al leerlo.
 */
const VIEW_KEY = 'tfp.view';

/**
 * Primer segmento de las rutas propias del empleado (ver `app.routes.ts`).
 *
 * <p>Las transversales —`notifications`, `auth`— quedan fuera a propósito: no
 * pertenecen a ninguna zona y visitarlas no debe cambiar de vista.
 */
const EMPLOYEE_SEGMENTS = new Set([
  'employee-dashboard',
  'workdays',
  'corrections',
  'absences',
  'shifts',
  'reports'
]);

/**
 * Decide qué zona de la aplicación se está usando: la de empleado o la de
 * administración.
 *
 * <p>Centraliza además la prioridad de rol que antes estaba repetida en el
 * guard, en la cabecera y en el login, y que además no coincidía entre ellos.
 *
 * <p>No autoriza nada: los guards siguen mirando los roles reales del JWT
 * (ver `role.guard.ts`). Esto solo elige el menú.
 */
@Injectable({ providedIn: 'root' })
export class ViewModeService {
  private readonly authService = inject(AuthService);
  private readonly chosen = signal<ViewMode | null>(null);

  /** Vistas a las que dan derecho los roles del usuario, en orden de prioridad. */
  readonly available = computed<ViewMode[]>(() => {
    const roles = this.authService.currentRoles() as Role[];
    return VIEW_PRIORITY.filter((view) => roles.includes(VIEW_ROLE[view]));
  });

  /**
   * Vista activa. Una elección que ya no corresponde a ningún rol —le han
   * quitado el de administrador, por ejemplo— se descarta aquí en lugar de
   * dejar la barra vacía; no hace falta limpiar lo almacenado.
   */
  readonly active = computed<ViewMode | null>(() => {
    const available = this.available();
    // Lo almacenado se lee aquí y no al construir el servicio: al arrancar, la
    // sesión aún se está recuperando y todavía no hay usuario con el que
    // comparar. Dentro del computed, además, se reevalúa al llegar el token.
    const chosen = this.chosen() ?? this.readStoredView();
    if (chosen && available.includes(chosen)) {
      return chosen;
    }
    return available[0] ?? null;
  });

  /** Solo tiene sentido ofrecer el cambio a quien puede estar en dos sitios. */
  readonly canSwitch = computed(() => this.available().length > 1);

  switchTo(view: ViewMode): void {
    if (!this.available().includes(view)) {
      return;
    }
    this.chosen.set(view);
    this.persist(view);
  }

  homeRouteFor(view: ViewMode): string {
    return VIEW_HOME_ROUTE[view];
  }

  /** Pantalla de inicio de quien entra o pulsa la marca. */
  homeRoute(): string {
    const active = this.active();
    return active ? VIEW_HOME_ROUTE[active] : VIEW_HOME_ROUTE.EMPLOYEE;
  }

  /**
   * Alinea la vista con la zona que se está visitando. Un enlace pegado o el
   * botón de atrás pueden llevar a la otra zona, y el menú no debe quedarse
   * mostrando la contraria a la pantalla.
   */
  syncWithUrl(url: string): void {
    const view = ViewModeService.viewOfUrl(url);
    if (view && this.available().includes(view) && this.active() !== view) {
      this.switchTo(view);
    }
  }

  private static viewOfUrl(url: string): ViewMode | null {
    // Por segmento y no por prefijo: `/shifts` y `/shiftsfoo` no son la misma
    // zona, y así los parámetros de consulta no estorban.
    const [firstSegment] = url.split(/[?#]/, 1)[0].split('/').filter(Boolean);
    if (!firstSegment) {
      return null;
    }
    if (firstSegment === 'platform') {
      return 'PLATFORM';
    }
    if (firstSegment === 'admin') {
      return 'ADMIN';
    }
    // Las rutas de empleado también cuentan: sin esto, ir desde la zona de
    // administración a un enlace guardado como `/workdays` dejaba el menú
    // mostrando Administración sobre una pantalla de empleado.
    return EMPLOYEE_SEGMENTS.has(firstSegment) ? 'EMPLOYEE' : null;
  }

  /*
   * El almacenamiento es una comodidad, nunca un motivo de fallo: en modo
   * privado o con la cuota agotada, tanto leer como escribir pueden lanzar.
   * Mismo criterio que la marca de sesión de `AuthService`.
   */
  private readStoredView(): ViewMode | null {
    try {
      const stored = localStorage.getItem(VIEW_KEY);
      if (!stored) {
        return null;
      }
      const separator = stored.indexOf(':');
      const owner = stored.slice(0, separator);
      const view = stored.slice(separator + 1) as ViewMode;
      return owner && owner === this.authService.currentUserId() ? view : null;
    } catch {
      return null;
    }
  }

  private persist(view: ViewMode): void {
    const userId = this.authService.currentUserId();
    if (!userId) {
      return;
    }
    try {
      localStorage.setItem(VIEW_KEY, `${userId}:${view}`);
    } catch {
      // Sin persistencia la vista dura lo que la pestaña; no es motivo de error.
    }
  }
}
