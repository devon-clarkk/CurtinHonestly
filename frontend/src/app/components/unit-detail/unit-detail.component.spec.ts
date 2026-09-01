import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BehaviorSubject } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { UnitDetailComponent } from './unit-detail.component';
import { AuthService } from '../../services/auth.service';
import { Review, UnitDetails } from '../../models/unit.model';

// Reviews are the only part of a unit page that is not the Curtin handbook, so
// they lead it. Nothing about that survives on its own: the order lives in the
// template, and the grid that puts the rail on the right would happily render a
// page whose source order had drifted back to handbook-first. These cases pin
// the order, and the two pieces of copy that stop the page asserting things it
// never explains: what verification means, and that a tip is worth leaving.

const REVIEW: Review = {
  id: 'review-1',
  rating: 4,
  reviewText: 'Steady until the week 8 assignment, then it bites.',
  professor: 'Dr Alina Verhoeven',
  workload: 6,
  hasExam: true,
  wouldTakeAgain: true,
  reviewerVerified: false,
  termType: 'SEMESTER_1',
  termYear: new Date().getFullYear(),
};

function unitWith(reviews: Review[]): UnitDetails {
  return {
    code: 'COMP1000',
    name: 'Introduction to Computer Science',
    description: 'Foundations of programming, algorithms and problem solving.',
    unitLink: 'https://handbook.curtin.edu.au/units/COMP1000',
    faculty: 'Science and Engineering',
    level: 'Undergraduate',
    area: 'Computing',
    fieldOfEducation: 'Computer Science',
    credits: 25,
    contactHours: 4,
    resultType: 'Grade/Mark',
    tuitionPatterns: [{ type: 'Lecture', duration: '1 x 2 Hours Weekly' }],
    prerequisiteGroups: [],
    numberOfReviews: reviews.length,
    averageRating: 4,
    averageWorkload: 6,
    averageFinalGrade: 72,
    wouldTakeAgainRatio: 1,
    reviews,
  };
}

describe('UnitDetailComponent', () => {
  let http: HttpTestingController;

  function renderWith(reviews: Review[]) {
    const paramMap = new BehaviorSubject({ get: (name: string) => (name === 'code' ? 'COMP1000' : null) });
    TestBed.configureTestingModule({
      imports: [UnitDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap, snapshot: { paramMap: paramMap.value } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    // Logged out is the state every visitor arrives in, and the one where the
    // tip invitation has to carry its own way in.
    TestBed.inject(AuthService).isLoggedIn.set(false);

    const fixture = TestBed.createComponent(UnitDetailComponent);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/units/COMP1000')).flush(unitWith(reviews));
    http.expectOne((r) => r.url.endsWith('/units/COMP1000/tips')).flush([]);
    fixture.detectChanges();
    return fixture;
  }

  function headings(fixture: { nativeElement: HTMLElement }): string[] {
    return [...fixture.nativeElement.querySelectorAll('h2')].map((h) => h.textContent!.replace(/\s+/g, ' ').trim());
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
  });

  it('puts the reviews ahead of every handbook section', () => {
    const fixture = renderWith([REVIEW]);
    const order = headings(fixture);

    expect(order[0]).toBe('COMP1000 student reviews');
    for (const handbookSection of ['What is COMP1000 about?', 'Unit details', 'How this unit is taught']) {
      expect(order.indexOf(handbookSection)).toBeGreaterThan(0);
    }
  });

  it('keeps every section at h2, so the page never skips a heading level', () => {
    const fixture = renderWith([REVIEW]);

    expect(headings(fixture).length).toBeGreaterThan(1);
    expect(fixture.nativeElement.querySelectorAll('h1').length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('h3, h4, h5, h6').length).toBe(0);
  });

  it('explains the verified badge wherever one is on screen', () => {
    const fixture = renderWith([{ ...REVIEW, reviewerVerified: true }]);
    const note = fixture.nativeElement.querySelector('.reviews-note').textContent;

    expect(note).toContain('Verified Curtin Student');
    expect(note).toContain('@student.curtin.edu.au');
  });

  it('leaves the badge unexplained where no review carries one', () => {
    const fixture = renderWith([REVIEW]);
    const note = fixture.nativeElement.querySelector('.reviews-note').textContent;

    expect(note).not.toContain('Verified Curtin Student');
    expect(fixture.componentInstance.hasVerifiedReviewer([REVIEW])).toBe(false);
    expect(fixture.componentInstance.hasVerifiedReviewer([])).toBe(false);
  });

  it('invites the first tip rather than reporting that none exist', () => {
    const fixture = renderWith([REVIEW]);
    const empty = fixture.nativeElement.querySelector('.tips-empty');

    expect(empty).not.toBeNull();
    expect(empty.textContent).not.toContain('No tips yet');
    expect(empty.querySelector('a[href="/login"]')).not.toBeNull();
  });
});
