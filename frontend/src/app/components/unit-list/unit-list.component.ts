import { Component, inject, OnInit, OnDestroy, ElementRef, ViewChild, PLATFORM_ID, TransferState, makeStateKey, signal } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { UnitService } from '../../services/unit.service';
import { SeoService } from '../../services/seo.service';
import { UnitRequestService } from '../../services/unit-request.service';
import { isResultsSeasonWindow } from '../../utils/results-season.util';
import { Page, UnitSummary, Faculty, UnitLevel } from '../../models/unit.model';
import { environment } from '../../../environments/environment';
import { BehaviorSubject, Subject } from 'rxjs';
import { debounceTime, takeUntil } from 'rxjs/operators';

interface UnitListState {
  units: UnitSummary[];
  page: number;
  totalPages: number;
  /** Nothing to show yet — the only state that gets a full-page spinner. */
  loading: boolean;
  /** Re-running the query with results already on screen (search, filter, sort). */
  refreshing: boolean;
  loadingMore: boolean;
  error: string | null;
  hasMore: boolean;
}

/**
 * The first page of the catalog, rendered during prerender and handed to the
 * browser inside the HTML.
 *
 * Angular's own HTTP transfer cache would nearly do this, but it skips any
 * request carrying an Authorization header, so a logged-in visitor would
 * hydrate a spinner over a server-rendered grid. An explicit key does not care
 * who is logged in.
 */
const UNIT_LIST_PAGE0 = makeStateKey<Page<UnitSummary>>('unit-list-page0');

@Component({
  selector: 'app-unit-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './unit-list.component.html',
  styleUrl: './unit-list.component.css'
})
export class UnitListComponent implements OnInit, OnDestroy {
  private unitService = inject(UnitService);
  private seoService = inject(SeoService);
  private unitRequestService = inject(UnitRequestService);
  private platformId = inject(PLATFORM_ID);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private transferState = inject(TransferState);

  private destroy$ = new Subject<void>();
  private searchSubject = new Subject<void>();
  private fetchCancel$ = new Subject<void>();
  private observer?: IntersectionObserver;

  readonly PAGE_SIZE = 24;

  searchQuery = '';
  selectedFaculties: Faculty[] = [];
  selectedLevel?: UnitLevel;
  sortBy = 'relevance';

  faculties = this.unitService.getFaculties();
  levels = this.unitService.getUnitLevels();
  sortOptions = [
    { value: 'relevance', label: 'Most Relevant' },
    { value: 'code', label: 'Unit Code (A-Z)' },
    { value: 'code_desc', label: 'Unit Code (Z-A)' },
    { value: 'name', label: 'Name (A-Z)' },
    { value: 'name_desc', label: 'Name (Z-A)' },
    { value: 'most_reviewed', label: 'Most Reviewed' },
    { value: 'least_reviewed', label: 'Least Reviewed' },
    { value: 'highest_rated', label: 'Highest Rated' },
    { value: 'lowest_rated', label: 'Lowest Rated' },
    { value: 'highest_mark', label: 'Highest Avg Grade' },
    { value: 'lowest_mark', label: 'Lowest Avg Grade' },
    { value: 'lowest_workload', label: 'Lowest Workload' },
    { value: 'highest_workload', label: 'Highest Workload' }
  ];

  private stateSubject = new BehaviorSubject<UnitListState>({
    units: [],
    page: 0,
    totalPages: 0,
    loading: true,
    refreshing: false,
    loadingMore: false,
    error: null,
    hasMore: false
  });

  readonly state$ = this.stateSubject.asObservable();

  // Reconnect IntersectionObserver whenever the sentinel appears/disappears
  @ViewChild('scrollSentinel', { static: false })
  set scrollSentinel(el: ElementRef<HTMLElement> | undefined) {
    // The sentinel now renders during prerender too, because the server draws a
    // real first page. There is no IntersectionObserver in Node, and nothing to
    // scroll there either.
    if (!isPlatformBrowser(this.platformId)) return;

    this.observer?.disconnect();
    if (el) {
      this.observer = new IntersectionObserver(
        entries => {
          if (entries[0].isIntersecting) {
            this.loadNextPage();
          }
        },
        { rootMargin: '200px' }
      );
      this.observer.observe(el.nativeElement);
    } else {
      this.observer = undefined;
    }
  }

  // Homepage nudge shown only in the ~2-week window after results release,
  // when students are actually motivated to write a review
  // (catalog-and-growth.md #4). Session-dismissible, not permanent.
  private readonly SEASONAL_BANNER_DISMISS_KEY = 'seasonal-review-banner-dismissed';
  showSeasonalBanner = signal(false);

  ngOnInit(): void {
    this.seoService.updateHomePage();

    if (!isPlatformBrowser(this.platformId)) {
      this.prerenderFirstPage();
      return;
    }

    if (isResultsSeasonWindow() && sessionStorage.getItem(this.SEASONAL_BANNER_DISMISS_KEY) !== 'true') {
      this.showSeasonalBanner.set(true);
    }

    // Search input is debounced; sort/faculty/level fire immediately
    this.searchSubject.pipe(debounceTime(300), takeUntil(this.destroy$)).subscribe(() => this.loadPage0());

    // Prerendered HTML already contains page 0. Adopt it instead of asking for
    // it again — this is what makes the first paint instant and lets hydration
    // match what the server drew.
    const prerendered = this.transferState.get(UNIT_LIST_PAGE0, null);
    if (prerendered) {
      this.transferState.remove(UNIT_LIST_PAGE0);
      this.applyPage0(prerendered);
    }

    // Always follow up with a real load. Prerendered data is as old as the last
    // deploy, and this page is nothing but review aggregates — freezing it there
    // would be worse than the spinner it replaced. With rows already on screen
    // loadPage0 refreshes in place instead of blanking, so this costs a dim and
    // no layout shift, and it seeds the TTL cache so the next reload inside the
    // window really is free.
    this.loadPage0();

    // The custom 404 page links here with ?request=1 to open the request-a-unit
    // form directly. The in-list form only renders in the empty state, so surface
    // a standalone request card at the top of the catalog instead.
    if (this.route.snapshot.queryParamMap.get('request')) {
      this.requestUnitCode = '';
      this.requestUnitNote = '';
      this.requestUnitError.set(null);
      this.requestUnitSubmitted.set(false);
      this.requestFromDeepLink.set(true);
    }
  }

  // Shows a request-a-unit card at the top of the list, independent of the
  // empty-state form, when arrived at via the 404 page's "Request a unit" link.
  requestFromDeepLink = signal(false);

  dismissSeasonalBanner(): void {
    this.showSeasonalBanner.set(false);
    sessionStorage.setItem(this.SEASONAL_BANNER_DISMISS_KEY, 'true');
  }

  ngOnDestroy(): void {
    this.fetchCancel$.next();
    this.fetchCancel$.complete();
    this.destroy$.next();
    this.destroy$.complete();
    this.observer?.disconnect();
  }

  /**
   * Renders the first page of the catalog into the prerendered HTML and hands
   * it to the browser via TransferState, so the homepage no longer ships as an
   * empty shell that only fills in after hydration.
   *
   * Gated on seoEnabled, which is true only for builds pointed at a real
   * backend. Those builds already require it to be reachable — fetch-unit-codes
   * fails the CI build outright if it is not — so this cannot introduce a new
   * way for a build to hang. Dev and local builds skip it exactly as before.
   *
   * If it fails anyway, leave the state untouched. That leaves the component in
   * `loading`, which prerenders exactly the markup it does today, and the
   * browser fetches normally. Better a spinner in the HTML than a baked-in
   * error every visitor then hydrates.
   */
  private prerenderFirstPage(): void {
    if (!environment.seoEnabled) return;

    this.fetchPage(0).subscribe({
      next: page => {
        this.applyPage0(page);
        this.transferState.set(UNIT_LIST_PAGE0, page);
      },
      error: () => {}
    });
  }

  private applyPage0(page: Page<UnitSummary>): void {
    this.stateSubject.next({
      units: this.normalizeRatios(page.content),
      page: 0,
      totalPages: page.totalPages,
      loading: false,
      refreshing: false,
      loadingMore: false,
      error: null,
      hasMore: page.totalPages > 1
    });
  }

  loadPage0(): void {
    this.fetchCancel$.next();
    const current = this.stateSubject.value;
    // Keep whatever is on screen while the new query runs. Blanking the grid on
    // every keystroke is what made searching feel like the page was reloading
    // the whole catalog; the full-page spinner is only for having nothing yet.
    const hasResults = current.units.length > 0;
    this.stateSubject.next({
      ...current,
      loading: !hasResults,
      refreshing: hasResults,
      page: 0,
      error: null
    });

    this.fetchPage(0).pipe(takeUntil(this.fetchCancel$)).subscribe({
      next: page => this.applyPage0(page),
      error: () => {
        this.stateSubject.next({
          ...this.stateSubject.value,
          loading: false,
          refreshing: false,
          error: 'Failed to load units.'
        });
      }
    });
  }

  private loadNextPage(): void {
    const state = this.stateSubject.value;
    if (state.loading || state.loadingMore || !state.hasMore) return;

    const nextPage = state.page + 1;
    this.stateSubject.next({ ...state, loadingMore: true });

    this.fetchPage(nextPage).pipe(takeUntil(this.fetchCancel$)).subscribe({
      next: page => {
        this.stateSubject.next({
          units: [...state.units, ...this.normalizeRatios(page.content)],
          page: nextPage,
          totalPages: page.totalPages,
          loading: false,
          refreshing: false,
          loadingMore: false,
          error: null,
          hasMore: nextPage < page.totalPages - 1
        });
      },
      error: () => {
        this.stateSubject.next({ ...this.stateSubject.value, loadingMore: false, error: 'Failed to load more units.' });
      }
    });
  }

  private fetchPage(page: number) {
    return this.unitService.getUnits(page, this.PAGE_SIZE, this.searchQuery, this.selectedFaculties, this.selectedLevel, this.sortBy);
  }

  private normalizeRatios(units: UnitSummary[]): UnitSummary[] {
    return units.map(unit => ({
      ...unit,
      wouldTakeAgainRatio: unit.wouldTakeAgainRatio > 1 ? unit.wouldTakeAgainRatio / 100 : unit.wouldTakeAgainRatio
    }));
  }

  onSearchChange(): void {
    this.searchSubject.next();
  }

  onFilterChange(): void {
    this.loadPage0();
  }

  toggleFaculty(faculty: Faculty): void {
    const index = this.selectedFaculties.indexOf(faculty);
    if (index > -1) {
      this.selectedFaculties.splice(index, 1);
    } else {
      this.selectedFaculties.push(faculty);
    }
    this.loadPage0();
  }

  isFacultySelected(faculty: Faculty): boolean {
    return this.selectedFaculties.includes(faculty);
  }

  resetFilters(): void {
    this.searchQuery = '';
    this.selectedFaculties = [];
    this.selectedLevel = undefined;
    this.sortBy = 'relevance';
    this.loadPage0();
  }

  // Side-by-side comparison — "which of these electives do I pick"
  // (catalog-and-growth.md #3). Capped at 4 so the comparison table stays readable.
  readonly MAX_COMPARE = 4;
  selectedForCompare = signal<string[]>([]);

  isSelectedForCompare(code: string): boolean {
    return this.selectedForCompare().includes(code);
  }

  toggleCompareSelection(code: string): void {
    const current = this.selectedForCompare();
    if (current.includes(code)) {
      this.selectedForCompare.set(current.filter(c => c !== code));
      return;
    }
    if (current.length >= this.MAX_COMPARE) {
      return;
    }
    this.selectedForCompare.set([...current, code]);
  }

  clearCompareSelection(): void {
    this.selectedForCompare.set([]);
  }

  goToCompare(): void {
    const codes = this.selectedForCompare();
    if (codes.length < 2) {
      return;
    }
    this.router.navigate(['/compare'], { queryParams: { units: codes.join(',') } });
  }

  // "Can't find your unit? Request it" — captured from the highest-intent
  // moment on the catalog (catalog-and-growth.md #1).
  showRequestUnitForm = signal(false);
  requestUnitCode = '';
  requestUnitNote = '';
  requestUnitSubmitting = signal(false);
  requestUnitSubmitted = signal(false);
  requestUnitError = signal<string | null>(null);

  openRequestUnitForm(): void {
    this.requestUnitCode = this.searchQuery;
    this.requestUnitNote = '';
    this.requestUnitError.set(null);
    this.requestUnitSubmitted.set(false);
    this.showRequestUnitForm.set(true);
  }

  submitUnitRequest(): void {
    const code = this.requestUnitCode.trim();
    if (!code) {
      return;
    }
    this.requestUnitSubmitting.set(true);
    this.requestUnitError.set(null);

    this.unitRequestService.requestUnit(code, this.requestUnitNote.trim() || undefined).subscribe({
      next: () => {
        this.requestUnitSubmitting.set(false);
        this.requestUnitSubmitted.set(true);
      },
      error: (err) => {
        this.requestUnitSubmitting.set(false);
        this.requestUnitError.set(err.error?.error || 'Could not submit your request. Please try again.');
      }
    });
  }

  getStarArray(rating: number): string[] {
    const stars: string[] = [];
    const fullStars = Math.floor(rating || 0);
    const hasHalfStar = (rating || 0) % 1 >= 0.5;
    for (let i = 1; i <= 5; i++) {
      if (i <= fullStars) {
        stars.push('full');
      } else if (i === fullStars + 1 && hasHalfStar) {
        stars.push('half');
      } else {
        stars.push('empty');
      }
    }
    return stars;
  }
}
