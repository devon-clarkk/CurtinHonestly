import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BehaviorSubject } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { UnitDetailComponent } from './unit-detail.component';
import { AuthService } from '../../services/auth.service';
import { PrerequisiteGraphService } from '../../services/prerequisite-graph.service';
import { PrerequisiteGroup, Review, UnitDetails } from '../../models/unit.model';

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

/**
 * A stand-in for the map scripts/fetch-unit-codes.js writes at build time. The
 * checked-in map is empty, so without one of these every case here would
 * exercise the "no graph was built" branch and nothing else.
 */
function graphOf(requiredFor: Record<string, string[]>): PrerequisiteGraphService {
  return {
    isAvailable: () => Object.keys(requiredFor).length > 0,
    unitsRequiring: (code: string) => requiredFor[code.toUpperCase()] ?? [],
    hasPage: (code: string) => requiredFor[code.toUpperCase()] !== undefined,
  } as unknown as PrerequisiteGraphService;
}

const EMPTY_GRAPH = graphOf({});

function unitWith(reviews: Review[], prerequisiteGroups: PrerequisiteGroup[] = []): UnitDetails {
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
    prerequisiteGroups,
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

  function renderWith(
    reviews: Review[],
    options: { graph?: PrerequisiteGraphService; prerequisiteGroups?: PrerequisiteGroup[] } = {}
  ) {
    // Reset here as well as in beforeEach: a case that renders more than one
    // page cannot reconfigure a TestBed that has already built a component.
    TestBed.resetTestingModule();
    const paramMap = new BehaviorSubject({ get: (name: string) => (name === 'code' ? 'COMP1000' : null) });
    TestBed.configureTestingModule({
      imports: [UnitDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap, snapshot: { paramMap: paramMap.value } } },
        { provide: PrerequisiteGraphService, useValue: options.graph ?? EMPTY_GRAPH },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    // Logged out is the state every visitor arrives in, and the one where the
    // tip invitation has to carry its own way in.
    TestBed.inject(AuthService).isLoggedIn.set(false);

    const fixture = TestBed.createComponent(UnitDetailComponent);
    fixture.detectChanges();
    http
      .expectOne((r) => r.url.endsWith('/units/COMP1000'))
      .flush(unitWith(reviews, options.prerequisiteGroups));
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

  // Which units a unit unlocks is the one fact on the page no handbook states,
  // and on the 1,729 units with no reviews it is the only fact the main column
  // has. It is also the one that can quietly become a lie: an empty map has to
  // stay silent rather than tell every unit in the catalogue it leads nowhere.
  describe('what the unit unlocks', () => {
    const GRAPH = graphOf({ COMP1000: ['COMP2003', 'COMP3001'] });

    it('lists and links every unit that requires this one', () => {
      const fixture = renderWith([REVIEW], { graph: GRAPH });
      const links = [...fixture.nativeElement.querySelectorAll('.required-for-link')];

      expect(links.map((a: HTMLAnchorElement) => a.textContent!.trim())).toEqual([
        'COMP2003',
        'COMP3001',
      ]);
      expect(links.map((a: HTMLAnchorElement) => a.getAttribute('href'))).toEqual([
        '/units/COMP2003',
        '/units/COMP3001',
      ]);
      expect(fixture.nativeElement.querySelector('.required-for-lead').textContent).toContain(
        'prerequisite for 2 units'
      );
    });

    it('says a dead end is a dead end, which is the useful half of the answer', () => {
      const fixture = renderWith([REVIEW], { graph: graphOf({ COMP2003: ['COMP3001'] }) });
      const none = fixture.nativeElement.querySelector('.required-for-none');

      expect(none).not.toBeNull();
      expect(none.textContent).toContain('not a gateway');
      expect(fixture.nativeElement.querySelector('.required-for-link')).toBeNull();
    });

    it('says nothing at all when the build produced no graph', () => {
      const fixture = renderWith([REVIEW]);

      expect(fixture.nativeElement.querySelector('.required-for-section')).toBeNull();
      expect(fixture.componentInstance.hasPrerequisiteGraph()).toBe(false);
    });

    // Reviews are the only thing long enough to hold a wide column open. A unit
    // that unlocks one other names one code, which does not close a gap of a
    // thousand pixels, so the unlock list does not keep the second column.
    it('drops to one column on any unit with no reviews', () => {
      const bare = renderWith([], { graph: graphOf({ COMP2003: ['COMP3001'] }) });
      expect(bare.nativeElement.querySelector('.content-grid-single')).not.toBeNull();

      const unlocking = renderWith([], { graph: GRAPH });
      expect(unlocking.nativeElement.querySelector('.content-grid-single')).not.toBeNull();
      expect(unlocking.nativeElement.querySelectorAll('.required-for-link').length).toBe(2);
    });

    it('keeps both columns wherever there is a review to read', () => {
      const reviewed = renderWith([REVIEW], { graph: graphOf({ COMP2003: ['COMP3001'] }) });
      expect(reviewed.nativeElement.querySelector('.content-grid-single')).toBeNull();
    });
  });

  // Prerequisite rows arrive from the handbook import, not the catalogue, so
  // what the data holds is not what a student should read.
  describe('prerequisite labels', () => {
    function groupWith(code: string, title: string): PrerequisiteGroup[] {
      return [
        {
          groupName: 'Prerequisites',
          requirement: 'one',
          position: 1,
          options: [{ code, title, concurrent: false }],
          courseOptions: [],
        },
      ];
    }

    it('prints the code the catalogue uses, not the versioned one', () => {
      const fixture = renderWith([REVIEW], {
        graph: graphOf({ COMP1005: ['COMP1000'] }),
        prerequisiteGroups: groupWith('COMP1005v1', 'Fundamentals of Programming'),
      });
      const link = fixture.nativeElement.querySelector('.prereq-link');

      expect(link.querySelector('strong').textContent.trim()).toBe('COMP1005');
      expect(link.getAttribute('href')).toBe('/units/COMP1005');
    });

    it('stops a legacy course row printing its own id twice', () => {
      const fixture = renderWith([REVIEW], {
        graph: graphOf({ COMP1005: ['COMP1000'] }),
        prerequisiteGroups: groupWith('1920', '1920 - Object Oriented Program Design 110'),
      });
      const row = fixture.nativeElement.querySelector('.prereq-link-plain');

      expect(row).not.toBeNull();
      expect(row.textContent.replace(/\s+/g, ' ').trim()).toBe(
        'Object Oriented Program Design 110'
      );
      expect(fixture.nativeElement.querySelector('.prereq-link[href]')).toBeNull();
    });

    // COMP1002 lists exactly this row in production: a code that resolves, has
    // no page left, and repeats itself inside its own title.
    it('handles a retired code whose title also repeats it', () => {
      const fixture = renderWith([REVIEW], {
        graph: graphOf({ COMP1005: ['COMP1000'] }),
        prerequisiteGroups: groupWith('COMP1001', 'COMP1001 - Object Oriented Program Design'),
      });
      const row = fixture.nativeElement.querySelector('.prereq-link-plain');

      expect(row.querySelector('strong').textContent.trim()).toBe('COMP1001');
      expect(row.querySelector('span').textContent.trim()).toBe('Object Oriented Program Design');
      expect(fixture.nativeElement.querySelector('.prereq-link[href]')).toBeNull();
    });

    it('does not link a retired code the catalogue no longer serves', () => {
      const fixture = renderWith([REVIEW], {
        graph: graphOf({ COMP1005: ['COMP1000'] }),
        prerequisiteGroups: groupWith('ACCT1000', 'Accounting'),
      });

      expect(fixture.nativeElement.querySelector('.prereq-link-plain')).not.toBeNull();
      expect(fixture.componentInstance.unitUrlCode('ACCT1000')).toBeUndefined();
      expect(fixture.componentInstance.unitUrlCode('COMP1005v1')).toBe('COMP1005');
    });

    it('falls back to shape alone where no graph was built', () => {
      const fixture = renderWith([REVIEW], {
        prerequisiteGroups: groupWith('ACCT1000', 'Accounting'),
      });

      expect(fixture.componentInstance.unitUrlCode('ACCT1000')).toBe('ACCT1000');
      expect(fixture.nativeElement.querySelector('.prereq-link[href]')).not.toBeNull();
    });
  });

  // Recency is the edge this site has over a competitor ranking on reviews from
  // 2020, so it is stated before any review is read. It has to be measured on
  // the term a reviewer took the unit, the axis the stale-data note uses, or
  // the page can date a review to this year directly under a line saying most
  // of them are from a previous one.
  describe('recency', () => {
    it('dates the reviews by the newest term any of them describes', () => {
      const fixture = renderWith([
        { ...REVIEW, termType: 'SEMESTER_1', termYear: 2024 },
        { ...REVIEW, id: 'review-2', termType: 'SEMESTER_2', termYear: 2025 },
        { ...REVIEW, id: 'review-3', termType: 'SEMESTER_1', termYear: 2025 },
      ]);

      expect(fixture.nativeElement.querySelector('.reviews-recency').textContent.trim()).toBe(
        'Most recent review covers Semester 2, 2025'
      );
    });

    it('reads the term taken rather than the date posted', () => {
      const posted = new Date().toISOString();
      const fixture = renderWith([
        { ...REVIEW, termType: 'SEMESTER_2', termYear: 2023, createdAt: posted },
      ]);
      const recency = fixture.nativeElement.querySelector('.reviews-recency').textContent;

      expect(recency).toContain('Semester 2, 2023');
      expect(recency).not.toContain(String(new Date().getFullYear()));
    });

    it('dates nothing when no review carries a term', () => {
      const fixture = renderWith([
        { ...REVIEW, termType: 'EARLIER_UNSPECIFIED', termYear: null },
        { ...REVIEW, id: 'review-2', termType: null, termYear: null },
      ]);

      expect(fixture.nativeElement.querySelector('.reviews-recency')).toBeNull();
      expect(fixture.componentInstance.latestReviewTerm([])).toBe('');
    });
  });
});
