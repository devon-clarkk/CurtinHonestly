import {
  Component,
  computed,
  inject,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
  TransferState,
  makeStateKey,
  signal,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, Observable, Subject } from 'rxjs';
import { catchError, map, switchMap, takeUntil, tap } from 'rxjs/operators';
import { IconComponent } from '../icon/icon.component';
import { UnitService } from '../../services/unit.service';
import { SeoService } from '../../services/seo.service';
import { Faculty, UnitSummary } from '../../models/unit.model';
import { environment } from '../../../environments/environment';
import { FacultyHub, facultyHubBySlug, facultyPagePath } from '../../utils/faculty.util';
import { groupUnitsByCodePrefix } from '../../utils/unit-seo.utils';

/**
 * Everything the API returns per unit is more than an index page renders, and
 * the largest faculty has 572 of them. Trimming before the list reaches
 * TransferState keeps the prerendered HTML to what it actually draws instead of
 * shipping every field twice.
 */
type IndexUnit = Pick<UnitSummary, 'code' | 'name' | 'numberOfReviews' | 'averageRating'>;

/**
 * The whole faculty in a single request. The API caps nothing and returns 572
 * units for the largest one in a single page, so there is no pagination to
 * carry through into URLs, and every unit stays exactly one link from its hub.
 */
const FACULTY_PAGE_SIZE = 2000;

/**
 * How the main listing is ordered. `code` is the default and the only order the
 * prerender ever produces: it is the one that groups the catalogue under its
 * subject headings, and the grouped list is what the hub is indexed for.
 */
export type UnitSort = 'code' | 'reviews' | 'rating';

/**
 * How many reviewed units the highlight module shows.
 *
 * Twelve fills three rows at desktop width, which is enough to read as a
 * shortlist without pushing the catalogue off the screen. Anything past that is
 * one click away: the module's own link switches the listing below to review
 * order, where the rest sit at the top.
 */
const MAX_HIGHLIGHTS = 12;

/** The id the highlight module's overflow link and the sort chips point at. */
const LISTING_ID = 'units';

function stateKeyFor(slug: string) {
  return makeStateKey<IndexUnit[]>(`faculty-units-${slug}`);
}

/**
 * Review count first, rating second: a unit with three reviews has more to read
 * than one with a single five, which is what "students are talking about" means.
 * Code last, so equal units keep a stable order instead of one the API happened
 * to return them in.
 */
function byReviewCount(a: IndexUnit, b: IndexUnit): number {
  return (
    b.numberOfReviews - a.numberOfReviews ||
    b.averageRating - a.averageRating ||
    a.code.localeCompare(b.code)
  );
}

/** The same three keys, rating first. Unreviewed units rate 0 and fall to the end. */
function byRating(a: IndexUnit, b: IndexUnit): number {
  return (
    b.averageRating - a.averageRating ||
    b.numberOfReviews - a.numberOfReviews ||
    a.code.localeCompare(b.code)
  );
}

@Component({
  selector: 'app-faculty',
  standalone: true,
  imports: [CommonModule, RouterLink, IconComponent],
  templateUrl: './faculty.component.html',
  styleUrl: './faculty.component.css'
})
export class FacultyComponent implements OnInit, OnDestroy {
  private unitService = inject(UnitService);
  private seoService = inject(SeoService);
  private route = inject(ActivatedRoute);
  private transferState = inject(TransferState);
  private platformId = inject(PLATFORM_ID);
  private destroy$ = new Subject<void>();

  hub = signal<FacultyHub | null>(null);
  groups = signal<{ prefix: string; units: IndexUnit[] }[]>([]);
  unitCount = signal(0);
  loading = signal(true);
  error = signal<string | null>(null);
  sort = signal<UnitSort>('code');

  /**
   * The faculty flat, alongside the grouped copy the A-Z listing renders.
   *
   * Grouping is one of three orders now rather than the only one, so the list
   * the API returned has to survive it. Both new views are derived from this
   * rather than stored, which is what keeps them impossible to leave stale.
   */
  private units = signal<IndexUnit[]>([]);

  /**
   * The reason the site exists, in the order worth reading.
   *
   * 1,761 units carry 32 reviews between them. Listed alphabetically, the units
   * that have something to say are scattered through several hundred that do
   * not, so the ones with reviews are pulled out and put first.
   */
  readonly reviewedUnits = computed(() =>
    this.units()
      .filter((unit) => unit.numberOfReviews > 0)
      .sort(byReviewCount)
  );

  readonly highlights = computed(() => this.reviewedUnits().slice(0, MAX_HIGHLIGHTS));

  /**
   * The main listing under a chosen order. Every unit is present in all three:
   * the chips re-order the catalogue, they never filter it, so no unit loses
   * its link on this page by being sorted.
   */
  readonly rankedUnits = computed(() => {
    const units = [...this.units()];
    return this.sort() === 'rating' ? units.sort(byRating) : units.sort(byReviewCount);
  });

  /**
   * Four of the five faculties have no reviewed unit at all, so the empty case
   * is the normal one and has to read as a state of the catalogue rather than a
   * module that failed to fill. It replaces the highlights outright, and only
   * once there is a loaded catalogue for it to describe.
   */
  readonly showNoReviewsNote = computed(
    () => !this.loading() && !this.error() && this.unitCount() > 0 && this.reviewedUnits().length === 0
  );

  /**
   * Driven by the route parameter, not read from it once.
   *
   * Every page carries all five hubs in the footer, so hub-to-hub is a normal
   * navigation. Angular reuses this component when only the parameter changes,
   * which means ngOnInit does not run again: a snapshot read would leave the
   * previous faculty's units, title, and canonical sitting under the new URL.
   * Same reason unit-detail drives itself from paramMap.
   */
  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        map((params) => params.get('slug') ?? ''),
        switchMap((slug) => this.load(slug)),
        takeUntil(this.destroy$)
      )
      .subscribe();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * switchMap upstream drops a request still in flight when the reader moves on,
   * so a slow faculty cannot land on top of the one they navigated to instead.
   */
  private load(slug: string): Observable<unknown> {
    const hub = facultyHubBySlug(slug);

    this.error.set(null);
    this.groups.set([]);
    this.units.set([]);
    this.unitCount.set(0);
    // A footer link is a hub-to-hub navigation on the same component, so a
    // chosen order would otherwise carry into a faculty the reader has not seen
    // yet, and land them on a ranked list with no subject headings.
    this.sort.set('code');

    if (!hub) {
      this.hub.set(null);
      this.loading.set(false);
      this.seoService.noIndex('Faculty not found | CurtinHonestly');
      return EMPTY;
    }

    this.hub.set(hub);
    this.loading.set(true);

    // The prerendered HTML carries the list, so the browser adopts it rather
    // than asking for several hundred units again. A catalogue index is only as
    // stale as the last deploy, and a deploy is what adds units to it, so there
    // is nothing here worth a second round trip. Only the hub that was
    // prerendered has a payload; navigating to another one fetches.
    const key = stateKeyFor(hub.slug);
    const prerendered = this.transferState.get(key, null);
    if (prerendered) {
      this.transferState.remove(key);
      this.apply(hub, prerendered);
      return EMPTY;
    }

    // Prerendering a dev build would bake a localhost fetch into the HTML, and
    // dev pages are noindex anyway. Mirrors unit-list's prerender guard.
    const isBrowser = isPlatformBrowser(this.platformId);
    if (!isBrowser && !environment.seoEnabled) {
      this.loading.set(false);
      return EMPTY;
    }

    return this.unitService
      .getUnits(0, FACULTY_PAGE_SIZE, undefined, [hub.faculty as Faculty], undefined, 'code')
      .pipe(
        tap((page) => {
          const units = page.content.map(
            ({ code, name, numberOfReviews, averageRating }): IndexUnit => ({
              code,
              name,
              numberOfReviews,
              averageRating,
            })
          );
          this.apply(hub, units);
          if (!isBrowser) {
            this.transferState.set(stateKeyFor(hub.slug), units);
          }
        }),
        catchError(() => {
          this.loading.set(false);
          this.error.set('Could not load units for this faculty. Please try again.');
          return EMPTY;
        })
      );
  }

  private apply(hub: FacultyHub, units: IndexUnit[]): void {
    this.units.set(units);
    this.groups.set(groupUnitsByCodePrefix(units));
    this.unitCount.set(units.length);
    this.loading.set(false);
    // Once per faculty, from the list the API returned. The ItemList is a claim
    // about which units this page links, and re-ordering the listing changes
    // neither that set nor its size. The prerendered order is the A-Z one.
    this.seoService.updateFacultyPage(hub, units);
  }

  /**
   * A jump-bar link has to carry this page's own path, not a bare fragment.
   * index.html sets `<base href="/">`, against which `#ACTL` resolves to
   * `/#ACTL`: the home page, with the fragment along for the ride. Spelling the
   * path out keeps it a same-document fragment, which the browser scrolls to
   * without a navigation, and keeps it a real link that works without
   * JavaScript.
   */
  jumpTarget(prefix: string): string {
    const hub = this.hub();
    return hub ? `${facultyPagePath(hub.slug)}#${prefix}` : `#${prefix}`;
  }

  /** The listing itself, for the same reason and by the same rule. */
  listingTarget(): string {
    return this.jumpTarget(LISTING_ID);
  }

  setSort(sort: UnitSort): void {
    this.sort.set(sort);
  }

  /**
   * The highlight module shows twelve of them; this is where the rest are. The
   * href stands on its own, so without JavaScript the link still lands the
   * reader on the catalogue instead of doing nothing.
   */
  showAllReviewed(): void {
    this.sort.set('reviews');
  }

  scoreLabel(unit: IndexUnit): string {
    return unit.averageRating.toFixed(1);
  }

  reviewCountLabel(unit: IndexUnit): string {
    return `${unit.numberOfReviews} ${unit.numberOfReviews === 1 ? 'review' : 'reviews'}`;
  }

  ratingLabel(unit: IndexUnit): string | null {
    if (!unit.numberOfReviews) {
      return null;
    }
    return `${unit.averageRating.toFixed(1)}/5 from ${unit.numberOfReviews} ${
      unit.numberOfReviews === 1 ? 'review' : 'reviews'
    }`;
  }
}
