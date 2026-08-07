import { Component, HostListener, inject, signal } from '@angular/core';

import { NotificationsService } from './features/notifications/notifications.service';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  private readonly authService = inject(AuthService);
  private readonly notificationsService = inject(NotificationsService);
  private readonly router = inject(Router);

  readonly menuOpen = signal(false);
  readonly unreadNotifications = signal(0);

  constructor() {
    // Cerrar el menú lateral al navegar a otra ruta.
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
      this.menuOpen.set(false);
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

  showEmployeeLinks(): boolean {
    return this.authService.hasRole('EMPLOYEE');
  }

  showAdminLinks(): boolean {
    return this.authService.hasRole('TENANT_ADMIN');
  }

  showPlatformLinks(): boolean {
    return this.authService.hasRole('PLATFORM_ADMIN');
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeMenu();
  }

  logout(): void {
    this.closeMenu();
    this.authService.logout().subscribe({
      next: () => void this.router.navigate(['/auth/login']),
      error: () => {
        this.authService.clearSession();
        void this.router.navigate(['/auth/login']);
      }
    });
  }
}
