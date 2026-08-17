import { Component, inject } from '@angular/core';

import { ClockService } from '../../core/services/clock.service';
import { BrandLockupComponent } from '../brand/brand-lockup.component';

/**
 * Pie comun a toda la app. Muestra la zona horaria del navegador porque es la
 * que interpreta cada hora de la interfaz: en un producto de control horario,
 * saber en que huso se estan leyendo los fichajes evita malentendidos.
 */
@Component({
  selector: 'app-footer',
  imports: [BrandLockupComponent],
  templateUrl: './app-footer.component.html',
  styleUrl: './app-footer.component.scss'
})
export class AppFooterComponent {
  private readonly clock = inject(ClockService);

  readonly timeZone = this.clock.timeZone;
  readonly year = new Date().getFullYear();
}
