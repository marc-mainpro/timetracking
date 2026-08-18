import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { AuthService } from './auth.service';
import { ViewModeService } from './view-mode.service';

describe('ViewModeService', () => {
  const USER_ID = 'user-1';

  function serviceWithRoles(roles: string[], userId: string | null = USER_ID): ViewModeService {
    const authService = TestBed.inject(AuthService);
    spyOn(authService, 'currentRoles').and.returnValue(roles);
    spyOn(authService, 'currentUserId').and.returnValue(userId);
    return TestBed.inject(ViewModeService);
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('ofrece solo las vistas a las que dan derecho los roles', () => {
    const service = serviceWithRoles(['EMPLOYEE']);

    expect(service.available()).toEqual(['EMPLOYEE']);
    expect(service.canSwitch()).toBeFalse();
    expect(service.active()).toBe('EMPLOYEE');
  });

  it('elige por prioridad cuando el usuario acumula roles', () => {
    const service = serviceWithRoles(['EMPLOYEE', 'TENANT_ADMIN']);

    expect(service.available()).toEqual(['ADMIN', 'EMPLOYEE']);
    expect(service.canSwitch()).toBeTrue();
    expect(service.active()).toBe('ADMIN');
  });

  it('recuerda la vista elegida entre recargas', () => {
    const service = serviceWithRoles(['EMPLOYEE', 'TENANT_ADMIN']);

    service.switchTo('EMPLOYEE');

    expect(service.active()).toBe('EMPLOYEE');
    expect(localStorage.getItem('tfp.view')).toBe(`${USER_ID}:EMPLOYEE`);
  });

  it('ignora la vista guardada por otro usuario del mismo navegador', () => {
    localStorage.setItem('tfp.view', 'otro-usuario:EMPLOYEE');

    const service = serviceWithRoles(['EMPLOYEE', 'TENANT_ADMIN']);

    expect(service.active()).toBe('ADMIN');
  });

  it('descarta la vista guardada cuando el rol ya no la habilita', () => {
    localStorage.setItem('tfp.view', `${USER_ID}:ADMIN`);

    const service = serviceWithRoles(['EMPLOYEE']);

    expect(service.active()).toBe('EMPLOYEE');
  });

  it('no falla cuando el almacenamiento lanza', () => {
    spyOn(localStorage, 'getItem').and.throwError('modo privado');
    spyOn(localStorage, 'setItem').and.throwError('modo privado');

    const service = serviceWithRoles(['EMPLOYEE', 'TENANT_ADMIN']);

    expect(() => service.switchTo('EMPLOYEE')).not.toThrow();
    expect(service.active()).toBe('EMPLOYEE');
  });

  it('no acepta una vista para la que no hay rol', () => {
    const service = serviceWithRoles(['EMPLOYEE']);

    service.switchTo('ADMIN');

    expect(service.active()).toBe('EMPLOYEE');
  });

  it('alinea la vista con la zona que se visita', () => {
    const service = serviceWithRoles(['EMPLOYEE', 'TENANT_ADMIN']);
    service.switchTo('EMPLOYEE');

    service.syncWithUrl('/admin/shifts');

    expect(service.active()).toBe('ADMIN');
  });

  it('vuelve a la vista de empleado al visitar una ruta suya', () => {
    const service = serviceWithRoles(['EMPLOYEE', 'TENANT_ADMIN']);
    expect(service.active()).toBe('ADMIN');

    // Un enlace guardado a una pantalla de empleado no puede dejar el menú
    // mostrando Administración.
    service.syncWithUrl('/workdays');

    expect(service.active()).toBe('EMPLOYEE');
  });

  it('reconoce la zona pese a los parámetros de consulta', () => {
    const service = serviceWithRoles(['EMPLOYEE', 'TENANT_ADMIN']);

    service.syncWithUrl('/employee-dashboard?tab=hoy');

    expect(service.active()).toBe('EMPLOYEE');
  });

  it('no confunde una ruta que solo empieza igual', () => {
    const service = serviceWithRoles(['EMPLOYEE', 'TENANT_ADMIN']);
    service.switchTo('EMPLOYEE');

    service.syncWithUrl('/administracion-externa');

    expect(service.active()).toBe('EMPLOYEE');
  });

  it('mantiene la zona de administración en sus propias rutas', () => {
    const service = serviceWithRoles(['EMPLOYEE', 'TENANT_ADMIN']);
    service.switchTo('EMPLOYEE');

    // `/admin/shifts` es de administración aunque `shifts` sea de empleado.
    service.syncWithUrl('/admin/shifts');

    expect(service.active()).toBe('ADMIN');
  });

  it('no cambia de vista en una ruta transversal', () => {
    const service = serviceWithRoles(['EMPLOYEE', 'TENANT_ADMIN']);
    service.switchTo('EMPLOYEE');

    service.syncWithUrl('/notifications');

    expect(service.active()).toBe('EMPLOYEE');
  });

  it('da la ruta de inicio de la vista activa', () => {
    const service = serviceWithRoles(['EMPLOYEE', 'TENANT_ADMIN']);

    expect(service.homeRoute()).toBe('/admin/employees');
    service.switchTo('EMPLOYEE');
    expect(service.homeRoute()).toBe('/employee-dashboard');
    expect(service.homeRouteFor('PLATFORM')).toBe('/platform/tenants');
  });
});
