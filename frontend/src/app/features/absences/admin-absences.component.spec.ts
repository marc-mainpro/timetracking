import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AdminAbsencesComponent } from './admin-absences.component';

describe('AdminAbsencesComponent', () => {
  let component: AdminAbsencesComponent;
  let fixture: ComponentFixture<AdminAbsencesComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminAbsencesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminAbsencesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads types and admin absences on init', () => {
    httpMock.expectOne('/api/v1/app/absence-types').flush([{ id: 'type-1', code: 'VAC', name: 'Vacaciones', requiresApproval: true, allowsAttachment: false, active: true }]);
    httpMock.expectOne((request) => request.url === '/api/v1/admin/absences').flush([]);
    httpMock
      .expectOne((request) => request.url === '/api/v1/employees')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });

    expect(component.approvedAbsences().length).toBe(0);
  });
});
