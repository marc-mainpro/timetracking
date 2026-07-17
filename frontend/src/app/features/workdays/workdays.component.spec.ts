import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { WorkdaysComponent } from './workdays.component';

describe('WorkdaysComponent', () => {
  let component: WorkdaysComponent;
  let fixture: ComponentFixture<WorkdaysComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkdaysComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    })
    .compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(WorkdaysComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    httpMock.expectOne((request) => request.url === '/api/v1/workdays').flush({
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0
    });
    expect(component).toBeTruthy();
  });
});
