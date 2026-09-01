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

/** A code on its own is a unit nobody has reviewed, which is most of them. */
type UnitSeed = string | { code: string; reviews: number; rating: number };

function reviewed(code: string, reviews: number, rating: number): UnitSeed {
  return { code, reviews, rating };
}

function unitsPage(seeds: UnitSeed[]) {
  return {
    content: seeds.map((seed) => {
      const unit = typeof seed === 'string' ? { code: seed, reviews: 0, rating: 0 } : seed;
      return {
        code: unit.code,
        name: `Unit ${unit.code}`,
        faculty: 'Humanities',
        level: 'Undergraduate',
        numberOfReviews: unit.reviews,
        averageRating: unit.rating,
        wouldTakeAgainRatio: 0,
      };
    }),
    totalElements: seeds.length,
    totalPages: 1,
    size: seeds.length,
    number: 0,
  };
}

function textOf(root: HTMLElement, selector: string): string[] {
  return [...root.querySelectorAll(selector)].map((el) => el.textContent!.trim());
}

/** The listing, in the order it is rendered in. */
function listedCodes(root: HTMLElement): string[] {
  return textOf(root, '.unit-index-code');
}

/** What the build guard counts: the distinct unit URLs this page offers. */
function linkedUnits(root: HTMLElement): Set<string> {
  return new Set(
    [...root.querySelectorAll('a[href^="/units/"]')].map((a) => a.getAttribute('href')!)
  );
}

function clickChip(root: HTMLElement, label: string): void {
  const chip = [...root.querySelectorAll<HTMLButtonElement>('.sort-chip')].find(
    (button) => button.textContent!.trim() === label
  );
  if (!chip) {
    throw new Error(`No sort chip labelled "${label}".`);
  }
  chip.click();
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

  function flush(slug: string, seeds: UnitSeed[]) {
    const request = http.expectOne(
      (r) => r.url.endsWith('/units') && r.params.get('faculties') === slug
    );
    request.flush(unitsPage(seeds));
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

    // A failed request is not a faculty with nothing reviewed in it, so the
    // note that says so must not stand in for the error.
    expect((fixture.nativeElement as HTMLElement).querySelector('.no-reviews-note')).toBeNull();
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

  // 1,761 units carry 32 reviews between them, all of them in one faculty. An
  // alphabetical index buries every one of those 32, so the hub leads with them.

  it('leads with the reviewed units, most reviewed first and best rated of equals ahead', () => {
    const fixture = createAt('humanities');
    flush('HUMANITIES', [
      'HUMN1000',
      reviewed('HUMN1004', 1, 5),
      reviewed('HUMN1003', 3, 2),
      reviewed('HUMN1002', 3, 4),
    ]);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(textOf(root, '.talked-about-code')).toEqual(['HUMN1002', 'HUMN1003', 'HUMN1004']);
    expect(textOf(root, '.talked-about-count')).toEqual(['3 reviews', '3 reviews', '1 review']);
    expect(root.querySelector('.talked-about-heading')?.textContent).toContain(
      'Units students are talking about'
    );
  });

  // Four of the five faculties have no reviewed unit at all, so this is the
  // ordinary state of a hub rather than an edge case.
  it('says so plainly instead of showing an empty module where nothing is reviewed', () => {
    const fixture = createAt('humanities');
    flush('HUMANITIES', ['HUMN1000', 'HUMN1001']);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.talked-about')).toBeNull();
    expect(root.querySelector('.no-reviews-note')?.textContent).toContain(
      'No Humanities unit has a review yet'
    );
  });

  it('caps the module and points the overflow at the listing in review order', () => {
    const fixture = createAt('humanities');
    const many = Array.from({ length: 14 }, (_, i) =>
      reviewed(`HUMN10${String(i).padStart(2, '0')}`, 14 - i, 5)
    );
    flush('HUMANITIES', many);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelectorAll('.talked-about-card')).toHaveLength(12);

    const more = root.querySelector('.talked-about-more a')!;
    expect(more.textContent).toContain('See all 14 reviewed units');
    // Same rule as the jump bar: a bare "#units" resolves against <base href="/">
    // and leaves the hub. Spelling the path out keeps it a same-document jump,
    // which is also what the link falls back to without JavaScript.
    expect(more.getAttribute('href')).toBe('/faculty/humanities#units');
    expect(root.querySelector('.unit-listing')?.id).toBe('units');

    fixture.componentInstance.showAllReviewed();
    fixture.detectChanges();
    expect(fixture.componentInstance.sort()).toBe('reviews');
    expect(listedCodes(root)[0]).toBe('HUMN1000');
  });

  it('re-orders the listing from the chips, keeping the jump bar to the grouped order', () => {
    const fixture = createAt('humanities');
    flush('HUMANITIES', ['HUMN1000', reviewed('MEDI1000', 2, 3), reviewed('HUMN1001', 1, 5)]);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(listedCodes(root)).toEqual(['HUMN1000', 'HUMN1001', 'MEDI1000']);
    expect(root.querySelector('.prefix-jump')).not.toBeNull();

    clickChip(root, 'Most reviewed');
    fixture.detectChanges();
    expect(listedCodes(root)).toEqual(['MEDI1000', 'HUMN1001', 'HUMN1000']);

    // The bar jumps between subject headings, and a ranked list has none.
    expect(root.querySelector('.prefix-jump')).toBeNull();
    expect(root.querySelectorAll('.prefix-group')).toHaveLength(0);

    clickChip(root, 'Highest rated');
    fixture.detectChanges();
    expect(listedCodes(root)).toEqual(['HUMN1001', 'MEDI1000', 'HUMN1000']);

    clickChip(root, 'A to Z');
    fixture.detectChanges();
    expect(listedCodes(root)).toEqual(['HUMN1000', 'HUMN1001', 'MEDI1000']);
    expect(root.querySelector('.prefix-jump')).not.toBeNull();
  });

  // What verify-seo-output.js counts in the built HTML, asserted here against
  // the DOM: the hub is the only page linking the whole catalogue, and it fails
  // the build below 50 links. Sorting re-orders the listing and must never cut
  // it, and the module above adds no unit the listing does not already carry.
  it('links every unit in the faculty whichever order is chosen', () => {
    const fixture = createAt('humanities');
    flush('HUMANITIES', ['HUMN1000', reviewed('MEDI1000', 2, 3), reviewed('HUMN1001', 1, 5)]);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const component = fixture.componentInstance;
    const expected = new Set(['/units/HUMN1000', '/units/HUMN1001', '/units/MEDI1000']);

    expect(root.querySelector('.talked-about')).not.toBeNull();
    expect(linkedUnits(root)).toEqual(expected);
    expect(linkedUnits(root).size).toBe(component.unitCount());

    for (const label of ['Most reviewed', 'Highest rated', 'A to Z']) {
      clickChip(root, label);
      fixture.detectChanges();
      expect(linkedUnits(root)).toEqual(expected);
    }
  });

  it('drops a chosen order when the reader moves to another faculty', () => {
    const fixture = createAt('humanities');
    flush('HUMANITIES', [reviewed('HUMN1000', 2, 4)]);
    fixture.detectChanges();

    clickChip(fixture.nativeElement as HTMLElement, 'Highest rated');
    fixture.detectChanges();

    paramMap.next(paramsFor('health-sciences'));
    flush('HEALTH_SCIENCES', ['NURS1000']);
    fixture.detectChanges();

    expect(fixture.componentInstance.sort()).toBe('code');
    expect((fixture.nativeElement as HTMLElement).querySelector('.prefix-group')).not.toBeNull();
  });
});
