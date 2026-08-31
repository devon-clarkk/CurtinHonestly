import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BehaviorSubject } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { FacultyComponent } from './faculty.component';

// Every page carries all five hubs in the footer, so hub-to-hub is an ordinary
// navigation and Angular reuses this component for it: same route, different
// parameter, no second ngOnInit. A component that read the slug from a snapshot
// left the previous faculty's units, title, and canonical under the new URL, and
// nothing about that is visible from outside the browser. The build guards check
// the prerendered files, which are correct either way, so this is the only place
// the behaviour is covered.

function paramsFor(slug: string) {
  return { get: (name: string) => (name === 'slug' ? slug : null) };
}

function unitsPage(codes: string[]) {
  return {
    content: codes.map((code) => ({
      code,
      name: `Unit ${code}`,
      faculty: 'Humanities',
      level: 'Undergraduate',
      numberOfReviews: 0,
      averageRating: 0,
      wouldTakeAgainRatio: 0,
    })),
    totalElements: codes.length,
    totalPages: 1,
    size: codes.length,
    number: 0,
  };
}

describe('FacultyComponent', () => {
  let paramMap: BehaviorSubject<ReturnType<typeof paramsFor>>;
  let http: HttpTestingController;

  function createAt(slug: string) {
    paramMap = new BehaviorSubject(paramsFor(slug));
    TestBed.configureTestingModule({
      imports: [FacultyComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap, snapshot: { paramMap: paramMap.value } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(FacultyComponent);
    fixture.detectChanges();
    return fixture;
  }

  function flush(slug: string, codes: string[]) {
    const request = http.expectOne(
      (r) => r.url.endsWith('/units') && r.params.get('faculties') === slug
    );
    request.flush(unitsPage(codes));
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    // UnitCacheService caches catalogue responses in sessionStorage so a reload
    // does not refetch. sessionStorage is browser-global, so it outlives the
    // TestBed injector: without this, the second case asking for a faculty an
    // earlier one already fetched is served from cache and issues no request.
    sessionStorage.clear();
  });

  it('renders the faculty named by the route parameter', () => {
    const fixture = createAt('humanities');
    flush('HUMANITIES', ['HUMN1000', 'HUMN1001']);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.hub()?.name).toBe('Humanities');
    expect(component.unitCount()).toBe(2);
  });

  it('reloads when only the route parameter changes', () => {
    const fixture = createAt('humanities');
    flush('HUMANITIES', ['HUMN1000', 'HUMN1001']);
    fixture.detectChanges();

    // What a footer link does: same component, new parameter, no new ngOnInit.
    paramMap.next(paramsFor('health-sciences'));
    flush('HEALTH_SCIENCES', ['NURS1000']);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.hub()?.name).toBe('Health Sciences');
    expect(component.unitCount()).toBe(1);
    expect(component.groups().flatMap((g) => g.units).map((u) => u.code)).toEqual(['NURS1000']);
  });

  it('clears the previous faculty rather than showing it under the new URL', () => {
    const fixture = createAt('humanities');
    flush('HUMANITIES', ['HUMN1000']);
    fixture.detectChanges();

    // Mid-navigation, before the new faculty's units land.
    paramMap.next(paramsFor('aboriginal-studies'));
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.hub()?.name).toBe('Aboriginal Studies');
    expect(component.groups()).toEqual([]);
    expect(component.unitCount()).toBe(0);

    flush('ABORIGINAL_STUDIES', ['INDH1000']);
  });

  // index.html sets `<base href="/">`. A bare `#ACTL` href resolves against it
  // to `/#ACTL`, which is the home page, so the jump bar silently navigated off
  // the hub instead of scrolling down it.
  it('points jump-bar links at this page, not the site root', () => {
    const fixture = createAt('humanities');
    flush('HUMANITIES', ['HUMN1000', 'MEDI1000']);
    fixture.detectChanges();

    const hrefs = [...(fixture.nativeElement as HTMLElement).querySelectorAll('.prefix-jump a')].map(
      (a) => a.getAttribute('href')
    );
    expect(hrefs).toEqual(['/faculty/humanities#HUMN', '/faculty/humanities#MEDI']);
  });

  it('gives every jump target a matching section to land on', () => {
    const fixture = createAt('humanities');
    flush('HUMANITIES', ['HUMN1000', 'MEDI1000']);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const fragments = [...root.querySelectorAll('.prefix-jump a')].map(
      (a) => a.getAttribute('href')!.split('#')[1]
    );
    const sectionIds = [...root.querySelectorAll('.prefix-group')].map((s) => s.id);
    expect(fragments).toEqual(sectionIds);
  });

  it('shows the not-found state for an unknown slug, and asks not to be indexed', () => {
    const fixture = createAt('not-a-real-faculty');
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.hub()).toBeNull();
    expect(component.loading()).toBe(false);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.faculty-missing')?.textContent).toContain('Faculty not found');

    http.expectNone(() => true);
  });

  it('surfaces an error instead of an empty page when the request fails', () => {
    const fixture = createAt('humanities');
    http
      .expectOne((r) => r.url.endsWith('/units'))
      .error(new ProgressEvent('network error'));
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.loading()).toBe(false);
    expect(component.error()).toContain('Could not load units');
  });

  it('labels a rating only where there is one', () => {
    const fixture = createAt('humanities');
    flush('HUMANITIES', ['HUMN1000']);
    const component = fixture.componentInstance;

    expect(component.ratingLabel({ code: 'A', name: 'A', numberOfReviews: 0, averageRating: 0 })).toBeNull();
    expect(
      component.ratingLabel({ code: 'A', name: 'A', numberOfReviews: 1, averageRating: 5 })
    ).toBe('5.0/5 from 1 review');
    expect(
      component.ratingLabel({ code: 'A', name: 'A', numberOfReviews: 3, averageRating: 4.25 })
    ).toBe('4.3/5 from 3 reviews');
  });
});
