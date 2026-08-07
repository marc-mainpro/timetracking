import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AbsencesComponent } from './absences.component';

describe('AbsencesComponent', () => {
  let component: AbsencesComponent;
  let fixture: ComponentFixture<AbsencesComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AbsencesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AbsencesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads types and absences on init', () => {
    httpMock.expectOne('/api/v1/app/absence-types').flush([{ id: 'type-1', code: 'VAC', name: 'Vacaciones', requiresApproval: true, allowsAttachment: false, active: true }]);
    httpMock.expectOne((request) => request.url === '/api/v1/app/absences').flush([]);

    expect(component.types().length).toBe(1);
    expect(component.absences().length).toBe(0);
  });
});
