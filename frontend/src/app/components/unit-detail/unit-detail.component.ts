import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UnitService } from '../../services/unit.service';
import { AuthService } from '../../services/auth.service';
import { ReviewService } from '../../services/review.service';
import { TipService } from '../../services/tip.service';
import { CompletedUnitsService } from '../../services/completed-units.service';
import { SeoService } from '../../services/seo.service';
import { reviewAuthorName } from '../../utils/unit-seo.utils';
import { GradeBand, gradeDistribution, gradedReviewCount } from '../../utils/grade-distribution.util';
import { formatTerm, termSortKey } from '../../utils/semester-options.util';
import { PrerequisiteGroup, REVIEW_TAGS, Review, Tip, UnitDetails } from '../../models/unit.model';
import { Observable, switchMap, map, of, tap, catchError } from 'rxjs';
import { AddReviewComponent } from '../add-review/add-review.component';
import { IconComponent } from '../icon/icon.component';

const MAX_TIP_LENGTH = 200;

/**
 * This component shows all the details for a single unit, including its reviews.
 * Beginners: We use 'ActivatedRoute' to get the unit code from the browser's URL.
 */
@Component({
  selector: 'app-unit-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AddReviewComponent, IconComponent],
  templateUrl: './unit-detail.component.html',
  styleUrl: './unit-detail.component.css'
})
export class UnitDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private unitService = inject(UnitService);
  private reviewService = inject(ReviewService);
  private tipService = inject(TipService);
  private completedUnitsService = inject(CompletedUnitsService);
  private seoService = inject(SeoService);
  authService = inject(AuthService);
  reviewAuthorName = reviewAuthorName;

  // This will store all the unit information once it's fetched
  unit$: Observable<UnitDetails> | undefined;

  // Track if we should show the add review form
  showAddReviewForm = signal(false);

  // Set when the backend rejects a submission because the user already reviewed this unit
  duplicateReviewMessage = signal<string | null>(null);

  likeErrorMessage = signal<string | null>(null);
  likingReviewId = signal<string | null>(null);

  // Report/flag (review-experience.md #8). Tracked client-side per page load —
  // the backend flag is idempotent either way, so this only affects whether the
  // button re-shows "Report" after a refresh, not whether a duplicate flag is created.
  flaggedReviewIds = signal<Set<string>>(new Set());
  flagErrorMessage = signal<string | null>(null);

  // Set when the unit fails to load (bad/removed code, or a network error) —
  // distinguishes "still loading" from "genuinely failed" so the spinner
  // doesn't spin forever (quick-fixes.md #2).
  loadError = signal(false);

  // Tips — short, lightweight contributions distinct from full reviews.
  tips = signal<Tip[]>([]);
  newTipText = '';
  readonly maxTipLength = MAX_TIP_LENGTH;
  tipError = signal<string | null>(null);
  isSubmittingTip = signal(false);
  private currentUnitCode = '';

  // PARKED - prerequisite eligibility checker (roadmap 4.4).
  //
  // The checker only works if a student has recorded every unit they have
  // completed, which almost nobody does, so it reported "you don't meet the
  // prerequisites" for nearly everyone. The markup was removed from the template
  // and the fetch below is no longer called; the backend endpoints, entities, and
  // DTOs are untouched.
  //
  // To restore: call loadCompletedUnits() from ngOnInit again, put back the
  // eligibility banner and the "Mark as completed" toggle in the template, and
  // re-add the group-status badge that uses groupStatusLabel/groupStatusClass.
  //
  // These members are intentionally retained rather than deleted - they are
  // exactly what restoring needs, and rewriting them from scratch is the only
  // alternative.
  completedUnitCodes = signal<Set<string>>(new Set());
  isUpdatingCompletedUnits = signal(false);
  completedUnitsError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadUnit();
  }

  private loadCompletedUnits() {
    this.completedUnitsService.getCompletedUnits().subscribe({
      next: (codes) => this.completedUnitCodes.set(new Set(codes.map(c => c.toUpperCase()))),
      error: () => {}
    });
  }

  isUnitCompleted(code: string): boolean {
    return this.completedUnitCodes().has(code.toUpperCase());
  }

  toggleUnitCompleted(code: string) {
    if (!this.authService.isLoggedIn() || this.isUpdatingCompletedUnits()) {
      return;
    }
    const upperCode = code.toUpperCase();
    const next = new Set(this.completedUnitCodes());
    if (next.has(upperCode)) {
      next.delete(upperCode);
    } else {
      next.add(upperCode);
    }

    this.completedUnitsError.set(null);
    this.isUpdatingCompletedUnits.set(true);
    this.completedUnitsService.updateCompletedUnits([...next]).subscribe({
      next: (codes) => {
        this.completedUnitCodes.set(new Set(codes.map(c => c.toUpperCase())));
        this.isUpdatingCompletedUnits.set(false);
      },
      error: (err) => {
        this.isUpdatingCompletedUnits.set(false);
        this.completedUnitsError.set(err.error?.error || 'Could not update your completed units. Please try again.');
      }
    });
  }

  // Labels/classes for the eligibility badges — kept as plain lookups (not
  // memoized) since they're evaluated per-group off a small, non-array
  // template value (no NG0103 risk like the array-returning methods below).
  groupStatusLabel(group: PrerequisiteGroup): string {
    if (group.satisfied === true) return '✅ Met';
    if (group.satisfied === false) return '❌ Not met';
    return '⚠️ Can\'t verify';
  }

  groupStatusClass(group: PrerequisiteGroup): string {
    if (group.satisfied === true) return 'status-met';
    if (group.satisfied === false) return 'status-unmet';
    return 'status-unverifiable';
  }

  eligibilityBannerClass(eligible: boolean | null | undefined): string {
    if (eligible === true) return 'status-met';
    if (eligible === false) return 'status-unmet';
    return 'status-unverifiable';
  }

  loadUnit() {
    this.loadError.set(false);
    this.selectedLecturer.set(null);
    // 1. Listen to changes in the URL parameters (like /units/COMP1000)
    // 2. Use 'switchMap' to switch from the URL stream to the API data stream
    this.unit$ = this.route.paramMap.pipe(
      switchMap(params => {
        const code = params.get('code') || '';
        if (!code) {
          this.loadError.set(true);
          return of(null as any);
        }
        return this.unitService.getUnitByCode(code).pipe(
          catchError(() => {
            this.loadError.set(true);
            return of(null as any);
          })
        );
      }),
      map((unit: UnitDetails) => {
        if (!unit) return unit;
        return {
          ...unit,
          // Ensure ratio is a decimal for the percentage pipe
          wouldTakeAgainRatio: unit.wouldTakeAgainRatio > 1 ? unit.wouldTakeAgainRatio / 100 : unit.wouldTakeAgainRatio
        };
      }),
      tap((unit) => {
        if (unit) {
          this.seoService.updateUnitPage(unit);
          this.currentUnitCode = unit.code;
          this.loadTips(unit.code);
        }
      })
    );
  }

  private loadTips(unitCode: string) {
    this.tipService.getTips(unitCode).subscribe({
      next: (tips) => this.tips.set(tips),
      error: () => this.tips.set([])
    });
  }

  submitTip() {
    const text = this.newTipText.trim();
    if (!text || text.length > this.maxTipLength || !this.currentUnitCode) {
      return;
    }

    this.tipError.set(null);
    this.isSubmittingTip.set(true);

    this.tipService.createTip(this.currentUnitCode, text).subscribe({
      next: (tip) => {
        this.isSubmittingTip.set(false);
        this.tips.update(existing => [tip, ...existing]);
        this.newTipText = '';
      },
      error: (err) => {
        this.isSubmittingTip.set(false);
        this.tipError.set(err.error?.error || 'Could not post tip. Please try again.');
      }
    });
  }

  deleteTip(tip: Tip) {
    if (!this.currentUnitCode) {
      return;
    }
    this.tipService.deleteTip(this.currentUnitCode, tip.id).subscribe({
      next: () => this.tips.update(existing => existing.filter(t => t.id !== tip.id)),
      error: (err) => this.tipError.set(err.error?.error || 'Could not delete tip. Please try again.')
    });
  }

  toggleAddReviewForm() {
    this.duplicateReviewMessage.set(null);
    this.showAddReviewForm.update(v => !v);
  }

  onReviewAdded() {
    this.showAddReviewForm.set(false);
    this.loadUnit();
  }

  onReviewError(message: string) {
    if (message.toLowerCase().includes('already reviewed')) {
      this.showAddReviewForm.set(false);
      this.duplicateReviewMessage.set(message);
    }
  }

  toggleLike(review: Review): void {
    if (!this.authService.isLoggedIn() || !review.id || this.likingReviewId()) {
      return;
    }

    this.likeErrorMessage.set(null);
    this.likingReviewId.set(review.id);

    const request$ = review.likedByCurrentUser
      ? this.reviewService.unlikeReview(review.id)
      : this.reviewService.likeReview(review.id);

    request$.subscribe({
      next: (result) => {
        review.likeCount = result.likeCount;
        review.likedByCurrentUser = result.likedByCurrentUser;
        this.likingReviewId.set(null);
      },
      error: (err) => {
        this.likingReviewId.set(null);
        this.likeErrorMessage.set(err.error?.error || 'Could not update like. Please try again.');
      }
    });
  }

  isFlagged(review: Review): boolean {
    return !!review.id && this.flaggedReviewIds().has(review.id);
  }

  flagReview(review: Review): void {
    if (!this.authService.isLoggedIn() || !review.id || this.isFlagged(review)) {
      return;
    }
    if (!confirm('Report this review to moderators for review?')) {
      return;
    }

    this.flagErrorMessage.set(null);
    this.reviewService.flagReview(review.id).subscribe({
      next: () => {
        this.flaggedReviewIds.update(ids => new Set(ids).add(review.id!));
      },
      error: (err) => {
        this.flagErrorMessage.set(err.error?.error || 'Could not report review. Please try again.');
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

  requirementLabel(requirement: string): string {
    return requirement === 'all' ? 'Complete All' : 'Select One';
  }

  private readonly tagLabels = new Map(REVIEW_TAGS.map(t => [t.value, t.label]));

  tagLabel(tag: string): string {
    return this.tagLabels.get(tag) || tag;
  }

  // Builds "Semester 1, 2026 • Prof. X • Jan 2026" from whichever parts are
  // present, without a dangling separator when a field is missing.
  reviewMetaLine(review: Review): string {
    const parts: string[] = [];
    const term = formatTerm(review.termType, review.termYear);
    if (term) {
      parts.push(term);
    }
    if (review.professor) {
      parts.push(`Prof. ${review.professor}`);
    }
    if (review.createdAt) {
      parts.push(this.formatReviewMonth(review.createdAt));
    }
    return parts.join(' • ');
  }

  private formatReviewMonth(iso: string): string {
    const date = new Date(iso);
    if (isNaN(date.getTime())) {
      return '';
    }
    return date.toLocaleDateString('en-AU', { month: 'short', year: 'numeric' });
  }

  // Percentage of loaded reviews where the reviewer reported an exam. All of a
  // unit's reviews are loaded on this page (no pagination), so this matches
  // what a server-computed aggregate would give.
  examPercentage(reviews: Review[]): number {
    if (!reviews || reviews.length === 0) {
      return 0;
    }
    const withExam = reviews.filter(r => r.hasExam).length;
    return Math.round((withExam / reviews.length) * 100);
  }

  // True when the most recent review predates the current calendar year —
  // a subtle signal that unit content (assessment, staff) may have changed since.
  /**
   * Whether the newest review describes a teaching period from a previous year.
   *
   * Measured on the term the unit was TAKEN, not when the review was posted.
   * Those diverge whenever someone reviews a unit from earlier in their degree,
   * which the semester dropdown now allows back to 2022 - a 2022 unit reviewed
   * today used to register as perfectly fresh, while the banner claimed
   * "assessment style, staff, or content may have changed since".
   *
   * Reviews with no term, or the open-ended "before 2022" bucket, have no
   * position on a timeline and are ignored rather than treated as ancient.
   */
  isReviewDataStale(reviews: Review[]): boolean {
    if (!reviews || reviews.length === 0) {
      return false;
    }

    const latestTermYear = reviews.reduce<number | null>((max, r) => {
      if (termSortKey(r.termType, r.termYear) === null) return max;
      const year = r.termYear as number;
      return max === null || year > max ? year : max;
    }, null);

    if (latestTermYear === null) {
      return false;
    }

    return latestTermYear < new Date().getFullYear();
  }

  // Lecturer-aware review display (review-experience.md #5). Deliberately
  // stops at "filter the review list by lecturer" — no standalone professor
  // pages, to avoid defamation/moderation exposure.
  selectedLecturer = signal<string | null>(null);

  // Both methods below are called directly from the template with `unit.reviews`.
  // Without memoization each call would allocate a new array, and returning a
  // fresh reference on every change-detection pass never lets the view
  // stabilize (NG0103: infinite change detection) — caught during browser
  // verification. Caching on reference/selection equality fixes it: `reviews`
  // keeps the same array identity for the life of one unit$ emission.
  private lecturerSummaryCache: { reviews: Review[]; result: { name: string; count: number }[] } | null = null;

  lecturerSummary(reviews: Review[]): { name: string; count: number }[] {
    if (this.lecturerSummaryCache?.reviews === reviews) {
      return this.lecturerSummaryCache.result;
    }
    const counts = new Map<string, number>();
    for (const review of reviews || []) {
      const name = review.professor?.trim();
      if (!name) continue;
      counts.set(name, (counts.get(name) || 0) + 1);
    }
    const result = [...counts.entries()]
      .map(([name, count]) => ({ name, count }))
      .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));
    this.lecturerSummaryCache = { reviews, result };
    return result;
  }

  private filteredReviewsCache: { reviews: Review[]; lecturer: string | null; result: Review[] } | null = null;

  filteredReviews(reviews: Review[]): Review[] {
    const lecturer = this.selectedLecturer();
    if (this.filteredReviewsCache?.reviews === reviews && this.filteredReviewsCache.lecturer === lecturer) {
      return this.filteredReviewsCache.result;
    }
    const result = lecturer ? reviews.filter(review => review.professor === lecturer) : reviews;
    this.filteredReviewsCache = { reviews, lecturer, result };
    return result;
  }

  // Grade distribution histogram (review-experience.md #7), gated at >= 5
  // graded reviews to avoid de-anonymizing a single reviewer. Memoized on
  // array reference for the same NG0103 reason as the two methods above —
  // gradeDistribution() otherwise allocates a fresh array on every call.
  readonly MIN_GRADED_REVIEWS_FOR_HISTOGRAM = 5;
  gradedReviewCount = gradedReviewCount;

  private gradeDistributionCache: { reviews: Review[]; result: GradeBand[] } | null = null;

  gradeDistribution(reviews: Review[]): GradeBand[] {
    if (this.gradeDistributionCache?.reviews === reviews) {
      return this.gradeDistributionCache.result;
    }
    const result = gradeDistribution(reviews);
    this.gradeDistributionCache = { reviews, result };
    return result;
  }
}
