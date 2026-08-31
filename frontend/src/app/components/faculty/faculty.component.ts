import { Component, inject, OnDestroy, OnInit, PLATFORM_ID, TransferState, makeStateKey, signal } from '@angular/core';
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

function stateKeyFor(slug: string) {
  return makeStateKey<IndexUnit[]>(`faculty-units-${slug}`);
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
    this.unitCount.set(0);

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
    this.groups.set(groupUnitsByCodePrefix(units));
    this.unitCount.set(units.length);
    this.loading.set(false);
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

  ratingLabel(unit: IndexUnit): string | null {
    if (!unit.numberOfReviews) {
      return null;
    }
    return `${unit.averageRating.toFixed(1)}/5 from ${unit.numberOfReviews} ${
      unit.numberOfReviews === 1 ? 'review' : 'reviews'
    }`;
  }
}
