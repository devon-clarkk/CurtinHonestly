import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { BehaviorSubject, debounceTime, distinctUntilChanged, map, Observable, switchMap } from 'rxjs';
import { ReviewService } from '../../services/review.service';
import { UnitService } from '../../services/unit.service';
import { SeoService } from '../../services/seo.service';
import { MyReview, RecognitionTier, ReviewerProfile, ReviewerTier, UnitSummary } from '../../models/unit.model';
import { formatTerm } from '../../utils/semester-options.util';
import {
  RECOGNITION_TIERS,
  REVIEWER_TIERS,
  nextRecognitionNudge,
  nextTierNudge,
  progressPercent,
} from '../../utils/reviewer-tier.util';
import { AddReviewComponent } from '../add-review/add-review.component';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-my-reviews',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AddReviewComponent, IconComponent],
  templateUrl: './my-reviews.component.html',
  styleUrl: './my-reviews.component.css'
})
export class MyReviewsComponent implements OnInit {
  private reviewService = inject(ReviewService);
  private unitService = inject(UnitService);
  private router = inject(Router);
  private seoService = inject(SeoService);

  // Labels are built client-side from the stored (termType, termYear) pair.
  formatTerm = formatTerm;

  // Rank panel helpers. Copy lives in reviewer-tier.util so the unit page,
  // the account page and this panel all say the same thing.
  nextTierNudge = nextTierNudge;
  nextRecognitionNudge = nextRecognitionNudge;
  progressPercent = progressPercent;

  reviews = signal<MyReview[]>([]);
  isLoading = signal(true);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  // Null until loaded, and stays null if the request fails: the panel simply
  // does not render, and the reviews list is never held up by it.
  profile = signal<ReviewerProfile | null>(null);

  showAddFlow = signal(false);
  selectedUnitCode = signal<string | null>(null);
  selectedUnitName = signal<string | null>(null);

  // Non-null while editing an existing review inline on its card.
  editingReview = signal<MyReview | null>(null);

  searchQuery = '';
  private searchSubject = new BehaviorSubject<string>('');
  searchResults$: Observable<UnitSummary[]> | undefined;

  ngOnInit() {
    this.seoService.noIndex('My Reviews | CurtinHonestly');
    this.loadReviews();
    this.loadProfile();

    this.searchResults$ = this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(query =>
        this.unitService.getUnits(0, 20, query || undefined, [], undefined, 'code')
      ),
      map(page => page.content.filter(unit => !this.hasReviewForUnit(unit.code)))
    );
  }

  loadReviews() {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.reviewService.getMyReviews().subscribe({
      next: (reviews) => {
        this.reviews.set(reviews);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Failed to load your reviews.');
        this.isLoading.set(false);
      }
    });
  }

  // Quiet on purpose: a rank that fails to load is not worth an error banner.
  loadProfile() {
    this.reviewService.getMyReviewerProfile().subscribe({
      next: (profile) => this.profile.set(profile),
      error: () => this.profile.set(null)
    });
  }

  tierGlyph(tier: ReviewerTier): string {
    return REVIEWER_TIERS[tier]?.glyph ?? '';
  }

  tierDescription(tier: ReviewerTier): string {
    return REVIEWER_TIERS[tier]?.description ?? '';
  }

  recognitionDescription(tier: RecognitionTier | null): string {
    return tier ? RECOGNITION_TIERS[tier]?.description ?? '' : '';
  }

  startAddReview() {
    this.editingReview.set(null);
    this.showAddFlow.set(true);
    this.selectedUnitCode.set(null);
    this.selectedUnitName.set(null);
    this.searchQuery = '';
    this.searchSubject.next('');
  }

  cancelAddReview() {
    this.showAddFlow.set(false);
    this.selectedUnitCode.set(null);
    this.selectedUnitName.set(null);
  }

  onSearchChange() {
    this.searchSubject.next(this.searchQuery);
  }

  selectUnit(unit: UnitSummary) {
    if (this.hasReviewForUnit(unit.code)) {
      this.errorMessage.set('You have already reviewed this unit. Delete your existing review below if you want to post a new one.');
      return;
    }
    this.selectedUnitCode.set(unit.code);
    this.selectedUnitName.set(unit.name);
    this.errorMessage.set(null);
  }

  onReviewAdded() {
    this.successMessage.set('Review submitted successfully.');
    this.cancelAddReview();
    this.loadReviews();
    this.loadProfile();
  }

  startEditReview(review: MyReview) {
    this.showAddFlow.set(false);
    this.editingReview.set(review);
  }

  cancelEditReview() {
    this.editingReview.set(null);
  }

  onReviewUpdated() {
    this.successMessage.set('Review updated successfully.');
    this.editingReview.set(null);
    this.loadReviews();
  }

  deleteReview(review: MyReview) {
    if (!confirm(`Delete your review for ${review.unitCode}?`)) {
      return;
    }

    this.reviewService.deleteReview(review.id).subscribe({
      next: () => {
        this.successMessage.set('Review deleted.');
        this.loadReviews();
        this.loadProfile();
      },
      error: () => this.errorMessage.set('Failed to delete review.')
    });
  }

  viewUnit(code: string) {
    this.router.navigate(['/units', code]);
  }

  private hasReviewForUnit(unitCode: string): boolean {
    return this.reviews().some(review => review.unitCode === unitCode);
  }
}
