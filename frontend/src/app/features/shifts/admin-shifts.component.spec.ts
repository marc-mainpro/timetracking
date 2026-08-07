import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AdminShiftsComponent } from './admin-shifts.component';

describe('AdminShiftsComponent', () => {
  let component: AdminShiftsComponent;
  let fixture: ComponentFixture<AdminShiftsComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminShiftsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminShiftsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads templates and employees on init', () => {
    httpMock.expectOne('/api/v1/admin/shifts/templates').flush([]);
    httpMock.expectOne((request) => request.url === '/api/v1/employees').flush({
      content: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0
    });
    expect(component.templates().length).toBe(0);
  });
});
