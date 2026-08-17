import { Component, input } from '@angular/core';

/**
 * Marca tipografica del producto: `TFP` en mono, hairline y el nombre visible
 * "Control horario". No hay logo ni assets de marca en el proyecto, asi que la
 * identidad se compone con las dos familias que ya usa la app.
 */
@Component({
  selector: 'app-brand-lockup',
  templateUrl: './brand-lockup.component.html',
  styleUrl: './brand-lockup.component.scss'
})
export class BrandLockupComponent {
  /** `sm` para el pie, `md` para las barras superiores. */
  readonly size = input<'sm' | 'md'>('md');

  /** El nombre completo se esconde donde solo cabe la sigla. */
  readonly showName = input(true);
}
