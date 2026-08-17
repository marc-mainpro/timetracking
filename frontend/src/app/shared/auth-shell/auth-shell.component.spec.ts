import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AuthShellComponent } from './auth-shell.component';
import { ClockService } from '../../core/services/clock.service';

@Component({
  imports: [AuthShellComponent],
  template: `
    <app-auth-shell eyebrow="Acceso" heading="Entra" [lede]="lede">
      <p class="projected">Formulario</p>
      <a authLinks href="#">Enlace</a>
    </app-auth-shell>
  `
})
class HostComponent {
  lede = 'Entradilla';
}

describe('AuthShellComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('pinta el antetítulo, el título y la entradilla', () => {
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('.eyebrow')?.textContent).toContain('Acceso');
    expect(element.querySelector('h1')?.textContent).toContain('Entra');
    expect(element.querySelector('.lede')?.textContent).toContain('Entradilla');
  });

  it('omite la entradilla cuando está vacía', () => {
    fixture.componentInstance.lede = '';
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.lede')).toBeNull();
  });

  it('proyecta el contenido y los enlaces secundarios en su hueco', () => {
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('.projected')).not.toBeNull();
    expect(element.querySelector('.auth-card__foot a')?.textContent).toContain('Enlace');
  });

  it('parte la hora para atenuar los segundos', () => {
    const component = fixture.debugElement.children[0].componentInstance as AuthShellComponent;

    expect(component.hoursAndMinutes()).toMatch(/^\d{2}:\d{2}$/);
    expect(component.seconds()).toMatch(/^:\d{2}$/);
  });

  it('traduce el avance del día a un ancho en porcentaje', () => {
    const clock = TestBed.inject(ClockService);
    const component = fixture.debugElement.children[0].componentInstance as AuthShellComponent;

    const expected = `${(clock.dayProgress() * 100).toFixed(2)}%`;
    expect(component.dayPercent()).toBe(expected);
  });
});
