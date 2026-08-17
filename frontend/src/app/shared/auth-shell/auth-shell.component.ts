import { DatePipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';

import { ClockService } from '../../core/services/clock.service';

/**
 * Envoltorio comun de las pantallas de acceso (entrar, recuperar y restablecer
 * contrasena). Antes cada una repetia el mismo `.auth-shell` + `.auth-card` +
 * titulo + entradilla en su propio SCSS; aqui vive una sola vez.
 *
 * La cabecera es la firma del producto: la hora en vivo con los segundos
 * atenuados y una hairline cuyo relleno mide la fraccion del dia transcurrida.
 * Al ser un valor, no una animacion, no necesita excepcion para
 * `prefers-reduced-motion`.
 */
@Component({
  selector: 'app-auth-shell',
  imports: [DatePipe],
  templateUrl: './auth-shell.component.html',
  styleUrl: './auth-shell.component.scss'
})
export class AuthShellComponent {
  private readonly clock = inject(ClockService);

  readonly eyebrow = input.required<string>();
  readonly heading = input.required<string>();
  readonly lede = input<string>('');

  readonly now = this.clock.now;
  readonly hoursAndMinutes = computed(() => this.formatted().slice(0, 5));
  readonly seconds = computed(() => this.formatted().slice(5));
  readonly dayPercent = computed(() => `${(this.clock.dayProgress() * 100).toFixed(2)}%`);

  private readonly formatted = computed(() => {
    const date = new Date(this.now());
    const pad = (value: number): string => `${value}`.padStart(2, '0');
    return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  });
}
