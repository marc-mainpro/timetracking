import { ComponentFixture, TestBed } from '@angular/core/testing';

import { WorkdaysComponent } from './workdays.component';

describe('WorkdaysComponent', () => {
  let component: WorkdaysComponent;
  let fixture: ComponentFixture<WorkdaysComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkdaysComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(WorkdaysComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
