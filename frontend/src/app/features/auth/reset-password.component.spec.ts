import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { ResetPasswordComponent } from './reset-password.component';

describe('ResetPasswordComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('pide el enlace de nuevo si la URL no trae token', async () => {
    const fixture = await createComponent({});

    expect(fixture.componentInstance.state()).toBe('missing-token');
    httpMock.expectNone('/api/v1/auth/password/reset');
  });

  it('no llama a la API si la contraseña es más corta que el mínimo del backend', async () => {
    const fixture = await createComponent({ token: 'un-token' });
    fixture.componentInstance.form.setValue({ newPassword: 'corta', confirmation: 'corta' });

    fixture.componentInstance.submit();

    httpMock.expectNone('/api/v1/auth/password/reset');
    expect(fixture.componentInstance.form.touched).toBeTrue();
  });

  it('no llama a la API si las dos contraseñas no coinciden', async () => {
    const fixture = await createComponent({ token: 'un-token' });
    fixture.componentInstance.form.setValue({
      newPassword: 'contrasenanueva',
      confirmation: 'contrasenaotra'
    });

    fixture.componentInstance.submit();

    httpMock.expectNone('/api/v1/auth/password/reset');
    expect(fixture.componentInstance.form.errors?.['mismatch']).toBeTrue();
  });

  it('envía el token con la contraseña nueva y confirma el cambio', async () => {
    const fixture = await createComponent({ token: 'un-token' });
    submit(fixture, 'contrasenanueva');

    const request = httpMock.expectOne('/api/v1/auth/password/reset');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ token: 'un-token', newPassword: 'contrasenanueva' });
    expect(request.request.withCredentials).toBeTrue();
    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(fixture.componentInstance.state()).toBe('done');
  });

  it('traduce el 401 de un enlace caducado sin salir de la pantalla', async () => {
    const fixture = await createComponent({ token: 'caducado' });
    submit(fixture, 'contrasenanueva');

    httpMock
      .expectOne('/api/v1/auth/password/reset')
      .flush({ errorCode: 'INVALID_PASSWORD_RESET_TOKEN' }, { status: 401, statusText: 'Unauthorized' });

    expect(fixture.componentInstance.state()).toBe('form');
    expect(fixture.componentInstance.errorMessage()).toContain('ya no es válido');
  });

  async function createComponent(
    queryParams: Record<string, string>
  ): Promise<ComponentFixture<ResetPasswordComponent>> {
    await TestBed.configureTestingModule({
      imports: [ResetPasswordComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } }
        }
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    fixture.detectChanges();
    return fixture;
  }

  function submit(fixture: ComponentFixture<ResetPasswordComponent>, password: string): void {
    fixture.componentInstance.form.setValue({
      newPassword: password,
      confirmation: password
    });
    fixture.componentInstance.submit();
  }
});
