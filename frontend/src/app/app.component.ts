import { DatePipe } from '@angular/common';
import { Component, ElementRef, HostListener, inject, signal, viewChild } from '@angular/core';

import { NotificationsService } from './features/notifications/notifications.service';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { AuthService } from './core/services/auth.service';
import { ViewModeService } from './core/services/view-mode.service';
import { ViewMode } from './core/models/role';
import { ClockService } from './core/services/clock.service';
import { AppFooterComponent } from './shared/app-footer/app-footer.component';
import { BrandLockupComponent } from './shared/brand/brand-lockup.component';

/** Un enlace de la navegación principal. */
interface NavLink {
  readonly path: string;
  readonly label: string;
}

/** Los enlaces se agrupan por ámbito para que no lleguen sueltos de dieciséis en dieciséis. */
interface NavGroup {
  readonly title: string;
  readonly links: readonly NavLink[];
}

const FOCUSABLE = 'a[href], button:not([disabled])';

/** Nombre de cada vista en el conmutador de la cabecera. */
const VIEW_LABELS: Readonly<Record<ViewMode, string>> = {
  PLATFORM: 'Plataforma',
  ADMIN: 'Administración',
  EMPLOYEE: 'Empleado'
};

/**
 * Enlaces que caben junto a la marca y el bloque de sesión sin apretar la barra.
 *
 * <p>Desde que la barra muestra una sola vista a la vez, el máximo son siete
 * (los seis de un ámbito más «Notificaciones») y la banda inferior ya no llega
 * a hacer falta. El límite se mantiene por si un ámbito crece: en línea, los
 * enlaces de más se parten en filas sueltas y los grupos dejan de leerse como
 * grupos.
 */
const INLINE_NAV_MAX_LINKS = 8;

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    DatePipe,
    AppFooterComponent,
    BrandLockupComponent
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  private readonly authService = inject(AuthService);
  private readonly viewMode = inject(ViewModeService);
  private readonly notificationsService = inject(NotificationsService);
  private readonly clock = inject(ClockService);
  private readonly router = inject(Router);

  private readonly menuToggle = viewChild<ElementRef<HTMLButtonElement>>('menuToggle');
  private readonly drawer = viewChild<ElementRef<HTMLElement>>('drawer');

  readonly menuOpen = signal(false);
  readonly unreadNotifications = signal(0);
  readonly currentUrl = signal('');
  readonly now = this.clock.now;

  constructor() {
    // Cerrar el menú lateral al navegar a otra ruta.
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe((event) => {
      this.menuOpen.set(false);
      const url = (event as NavigationEnd).urlAfterRedirects;
      this.currentUrl.set(url);
      // Un enlace guardado o el botón de atrás pueden llevar a la otra zona:
      // el menú debe acompañar a la pantalla, no contradecirla.
      this.viewMode.syncWithUrl(url);
      this.refreshUnreadNotifications();
    });
  }

  /**
   * Refresca el contador de no leidas. No hay push del servidor, asi que se
   * consulta al entrar y tras marcar como leida; un sondeo continuo seria
   * trafico constante para un dato que cambia poco.
   */
  refreshUnreadNotifications(): void {
    if (!this.authService.isAuthenticated()) {
      this.unreadNotifications.set(0);
      return;
    }
    this.notificationsService.unreadCount().subscribe({
      next: (result) => this.unreadNotifications.set(result.unread),
      error: () => this.unreadNotifications.set(0)
    });
  }

  isAuthenticated(): boolean {
    return this.authService.isAuthenticated();
  }

  /*
   * El menú sigue a la vista activa, no a los roles: quien administra y además
   * ficha conserva el acceso a ambas zonas —los guards miran el JWT—, pero ve
   * los enlaces de una cada vez.
   */
  showEmployeeLinks(): boolean {
    return this.viewMode.active() === 'EMPLOYEE';
  }

  showAdminLinks(): boolean {
    return this.viewMode.active() === 'ADMIN';
  }

  showPlatformLinks(): boolean {
    return this.viewMode.active() === 'PLATFORM';
  }

  /** Solo se ofrece a quien tiene derecho a más de una zona. */
  canSwitchView(): boolean {
    return this.viewMode.canSwitch();
  }

  availableViews(): readonly ViewMode[] {
    return this.viewMode.available();
  }

  activeView(): ViewMode | null {
    return this.viewMode.active();
  }

  viewLabel(view: ViewMode): string {
    return VIEW_LABELS[view];
  }

  switchView(view: ViewMode): void {
    this.viewMode.switchTo(view);
    this.closeMenu();
    // La pantalla actual puede no pertenecer a la vista nueva; quedarse en ella
    // dejaría el menú mostrando una zona y el contenido otra.
    void this.router.navigate([this.viewMode.homeRouteFor(view)]);
  }

  /** Estamos en la pantalla de entrar: el header ofrece la acción contraria. */
  onLoginPage(): boolean {
    return this.currentUrl().startsWith('/auth/login');
  }

  /** La marca lleva al inicio de la vista activa, no siempre a la misma ruta. */
  homeRoute(): string {
    return this.viewMode.homeRoute();
  }

  navGroups(): readonly NavGroup[] {
    const groups: NavGroup[] = [];

    if (this.showEmployeeLinks()) {
      groups.push({
        // El grupo no puede llamarse igual que su primer enlace.
        title: 'Mi tiempo',
        links: [
          { path: '/employee-dashboard', label: 'Mi jornada' },
          { path: '/workdays', label: 'Historial' },
          { path: '/corrections', label: 'Correcciones' },
          { path: '/absences', label: 'Ausencias' },
          { path: '/shifts', label: 'Turnos' },
          { path: '/reports', label: 'Mis informes' }
        ]
      });
    }

    if (this.showAdminLinks()) {
      groups.push({
        title: 'Administración',
        links: [
          { path: '/admin/employees', label: 'Empleados' },
          { path: '/admin/calendars', label: 'Calendarios' },
          { path: '/admin/corrections', label: 'Revisiones' },
          { path: '/admin/absences', label: 'Ausencias' },
          { path: '/admin/shifts', label: 'Gestión turnos' },
          { path: '/admin/reports', label: 'Informes' }
        ]
      });
    }

    if (this.showPlatformLinks()) {
      groups.push({
        title: 'Plataforma',
        links: [
          { path: '/platform/tenants', label: 'Tenants' },
          { path: '/platform/registrations', label: 'Solicitudes' },
          { path: '/platform/system-status', label: 'Estado' }
        ]
      });
    }

    groups.push({
      title: 'Cuenta',
      links: [{ path: '/notifications', label: 'Notificaciones' }]
    });

    return groups;
  }

  /** Solo en escritorio decide nada: en móvil los enlaces viven en el cajón. */
  inlineNav(): boolean {
    const links = this.navGroups().reduce((total, group) => total + group.links.length, 0);
    return links <= INLINE_NAV_MAX_LINKS;
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
    if (this.menuOpen()) {
      // Al abrir, el foco entra en el cajón; si no, seguiría detrás del backdrop.
      queueMicrotask(() => this.focusableInDrawer()[0]?.focus());
    }
  }

  closeMenu(): void {
    if (!this.menuOpen()) {
      return;
    }
    this.menuOpen.set(false);
    this.menuToggle()?.nativeElement.focus();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeMenu();
  }

  /**
   * Mientras el cajón está abierto tapa la página con un backdrop, así que el
   * tabulador tiene que quedarse dentro: sin esto el foco se pierde en los
   * controles ocultos que hay debajo.
   */
  @HostListener('document:keydown.tab', ['$event'])
  @HostListener('document:keydown.shift.tab', ['$event'])
  onTab(event: KeyboardEvent): void {
    if (!this.menuOpen()) {
      return;
    }
    const focusable = this.focusableInDrawer();
    if (focusable.length === 0) {
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement;

    if (event.shiftKey && (active === first || active === this.menuToggle()?.nativeElement)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }

  logout(): void {
    this.menuOpen.set(false);
    this.authService.logout().subscribe({
      next: () => void this.router.navigate(['/auth/login']),
      error: () => {
        this.authService.clearSession();
        void this.router.navigate(['/auth/login']);
      }
    });
  }

  private focusableInDrawer(): HTMLElement[] {
    const element = this.drawer()?.nativeElement;
    return element ? Array.from(element.querySelectorAll<HTMLElement>(FOCUSABLE)) : [];
  }
}
