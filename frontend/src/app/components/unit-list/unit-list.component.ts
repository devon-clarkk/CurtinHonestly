import { Component, computed, inject, OnInit, OnDestroy, ElementRef, NgZone, ViewChild, PLATFORM_ID, TransferState, makeStateKey, signal } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { UnitService } from '../../services/unit.service';
import { SeoService } from '../../services/seo.service';
import { UnitRequestService } from '../../services/unit-request.service';
import { AuthService } from '../../services/auth.service';
import { RecommendationService } from '../../services/recommendation.service';
import { isResultsSeasonWindow } from '../../utils/results-season.util';
import { Page, UnitSummary, Faculty, UnitLevel } from '../../models/unit.model';
import { Recommendations } from '../../models/recommendation.model';
import { environment } from '../../../environments/environment';
import { HomeEventsStripComponent } from '../events/home-events-strip/home-events-strip.component';
import { BehaviorSubject, Subject } from 'rxjs';
import { debounceTime, takeUntil } from 'rxjs/operators';

interface UnitListState {
  units: UnitSummary[];
  page: number;
  totalPages: number;
  /** Total units matching the current query, for the "Showing X of Y" hint. */
  totalElements: number;
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
  imports: [CommonModule, RouterLink, FormsModule, HomeEventsStripComponent],
  templateUrl: './unit-list.component.html',
  styleUrl: './unit-list.component.css'
})
export class UnitListComponent implements OnInit, OnDestroy {
  private unitService = inject(UnitService);
  private seoService = inject(SeoService);
  private unitRequestService = inject(UnitRequestService);
  private authService = inject(AuthService);
  private recommendationService = inject(RecommendationService);
  private platformId = inject(PLATFORM_ID);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private transferState = inject(TransferState);
  private ngZone = inject(NgZone);

  private destroy$ = new Subject<void>();
  private searchSubject = new Subject<void>();
  private fetchCancel$ = new Subject<void>();
  private observer?: IntersectionObserver;

  readonly PAGE_SIZE = 24;

  /**
   * How many pages scroll in on their own before the list hands over to the
   * "Show more units" button. Three pages is 72 cards, enough that casual
   * browsing never sees the button, while the footer stays reachable on a
   * catalogue of about 1,760 units: once the button is in charge, nothing
   * appends itself under the visitor as they approach the bottom of the page.
   */
  readonly AUTO_LOAD_PAGES = 3;

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
    totalElements: 0,
    loading: true,
    refreshing: false,
    loadingMore: false,
    error: null,
    hasMore: false
  });

  readonly state$ = this.stateSubject.asObservable();

  // Reconnect IntersectionObserver whenever the sentinel appears/disappears.
  // The template only renders the sentinel while autoLoadActive() holds, so
  // once the "Show more units" button takes over the observer is torn down
  // here (el is undefined) and cannot fire again until a new query resets the
  // list to page 0 and the sentinel comes back.
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

  /**
   * True while the next page should scroll in on its own. Pages 0 to
   * AUTO_LOAD_PAGES - 1 arrive this way; after that the visitor asks for more
   * with the button, so the page has a real bottom and the footer is reachable.
   */
  autoLoadActive(state: UnitListState): boolean {
    return state.page + 1 < this.AUTO_LOAD_PAGES;
  }

  /** Percentage of the current result set that is on screen, for the progress bar. */
  loadedPercent(state: UnitListState): number {
    if (state.totalElements <= 0) return 0;
    return Math.min(100, Math.round((state.units.length / state.totalElements) * 100));
  }

  /**
   * Polite live-region text for screen reader users after a manual "Show more".
   * Auto-loaded pages stay silent: announcing every scroll-triggered page would
   * be noise, and the button is the only load the visitor asked for by name.
   */
  loadAnnouncement = signal('');
  private nextLoadIsManual = false;

  /**
   * When the button fetches the final page it disappears with it, which would
   * drop keyboard focus to the top of the document. The end-of-catalogue
   * message picks focus up instead, once it has rendered.
   */
  private focusEndWhenRendered = false;

  @ViewChild('catalogEnd', { static: false })
  set catalogEnd(el: ElementRef<HTMLElement> | undefined) {
    if (!el || !this.focusEndWhenRendered) return;
    this.focusEndWhenRendered = false;
    el.nativeElement.focus({ preventScroll: true });
  }

  /** The "Show more units" button. */
  loadMore(): void {
    this.nextLoadIsManual = true;
    this.loadNextPage();
  }

  // Floating "Back to top" control, shown once the visitor is about two
  // screens down. Browser only: registered in ngOnInit after the platform
  // check and removed in ngOnDestroy.
  showBackToTop = signal(false);
  private scrollListener?: () => void;

  private watchScrollForBackToTop(): void {
    const update = () => {
      this.showBackToTop.set(window.scrollY > window.innerHeight * 2);
    };
    this.scrollListener = update;
    // Scroll fires constantly; keep it off the change detection path. Setting a
    // signal to the value it already holds is a no-op, so the component only
    // re-renders on the two transitions that matter.
    this.ngZone.runOutsideAngular(() => {
      window.addEventListener('scroll', update, { passive: true });
    });
    update();
  }

  scrollToTop(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    window.scrollTo({ top: 0, behavior: reduceMotion ? 'auto' : 'smooth' });
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

    this.watchScrollForBackToTop();
    this.loadForYou();

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

  /**
   * "For you" teaser above the catalogue: the signed-in student's top picks,
   * or the nudge to review more units when the model has nothing personal yet.
   *
   * Browser only, and only after ngOnInit's platform check: the signal stays
   * null during prerender and through hydration (the request has not answered
   * yet), so the server-drawn markup and TransferState handling above are
   * untouched. A logged-out visitor never issues the request.
   */
  readonly FOR_YOU_LIMIT = 4;
  forYou = signal<Recommendations | null>(null);
  // Sliced once per result rather than in the template, so the list keeps one
  // array identity across change detection.
  forYouTop = computed(() => this.forYou()?.recommended.slice(0, this.FOR_YOU_LIMIT) ?? []);

  private loadForYou(): void {
    if (!this.authService.isLoggedIn()) {
      return;
    }
    this.recommendationService.getForMe().subscribe({
      next: result => this.forYou.set(result),
      error: () => this.forYou.set(null)
    });
  }

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
    if (this.scrollListener && isPlatformBrowser(this.platformId)) {
      window.removeEventListener('scroll', this.scrollListener);
      this.scrollListener = undefined;
    }
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
      totalElements: page.totalElements,
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
    if (state.loading || state.loadingMore || !state.hasMore) {
      this.nextLoadIsManual = false;
      return;
    }

    const manual = this.nextLoadIsManual;
    this.nextLoadIsManual = false;
    const nextPage = state.page + 1;
    this.stateSubject.next({ ...state, loadingMore: true });

    this.fetchPage(nextPage).pipe(takeUntil(this.fetchCancel$)).subscribe({
      next: page => {
        const units = [...state.units, ...this.normalizeRatios(page.content)];
        const hasMore = nextPage < page.totalPages - 1;
        this.stateSubject.next({
          units,
          page: nextPage,
          totalPages: page.totalPages,
          totalElements: page.totalElements,
          loading: false,
          refreshing: false,
          loadingMore: false,
          error: null,
          hasMore
        });
        if (manual) {
          const added = page.content.length;
          this.loadAnnouncement.set(
            `Loaded ${added} more unit${added === 1 ? '' : 's'}. Showing ${units.length} of ${page.totalElements}.`
          );
          this.focusEndWhenRendered = !hasMore;
        }
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
