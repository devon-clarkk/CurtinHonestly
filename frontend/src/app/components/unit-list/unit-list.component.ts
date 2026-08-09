import { Component, inject, OnInit, OnDestroy, ElementRef, ViewChild, PLATFORM_ID, signal } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { UnitService } from '../../services/unit.service';
import { SeoService } from '../../services/seo.service';
import { UnitRequestService } from '../../services/unit-request.service';
import { isResultsSeasonWindow } from '../../utils/results-season.util';
import { UnitSummary, Faculty, UnitLevel } from '../../models/unit.model';
import { BehaviorSubject, Subject } from 'rxjs';
import { debounceTime, takeUntil } from 'rxjs/operators';

interface UnitListState {
  units: UnitSummary[];
  page: number;
  totalPages: number;
  loading: boolean;
  loadingMore: boolean;
  error: string | null;
  hasMore: boolean;
}

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
    loadingMore: false,
    error: null,
    hasMore: false
  });

  readonly state$ = this.stateSubject.asObservable();

  // Reconnect IntersectionObserver whenever the sentinel appears/disappears
  @ViewChild('scrollSentinel', { static: false })
  set scrollSentinel(el: ElementRef<HTMLElement> | undefined) {
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

    // Skip all data fetching during SSR prerender — avoids CI build timeouts.
    // The browser hydrates and fetches data client-side via the async pipe.
    if (!isPlatformBrowser(this.platformId)) return;

    if (isResultsSeasonWindow() && sessionStorage.getItem(this.SEASONAL_BANNER_DISMISS_KEY) !== 'true') {
      this.showSeasonalBanner.set(true);
    }

    // Search input is debounced; sort/faculty/level fire immediately
    this.searchSubject.pipe(debounceTime(300), takeUntil(this.destroy$)).subscribe(() => this.loadPage0());
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

  loadPage0(): void {
    this.fetchCancel$.next();
    const current = this.stateSubject.value;
    this.stateSubject.next({ ...current, loading: true, units: [], page: 0, error: null, hasMore: false });

    this.fetchPage(0).pipe(takeUntil(this.fetchCancel$)).subscribe({
      next: page => {
        this.stateSubject.next({
          units: this.normalizeRatios(page.content),
          page: 0,
          totalPages: page.totalPages,
          loading: false,
          loadingMore: false,
          error: null,
          hasMore: page.totalPages > 1
        });
      },
      error: () => {
        this.stateSubject.next({ ...this.stateSubject.value, loading: false, error: 'Failed to load units.' });
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
