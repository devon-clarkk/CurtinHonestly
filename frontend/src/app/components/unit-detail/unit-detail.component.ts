import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UnitService } from '../../services/unit.service';
import { AuthService } from '../../services/auth.service';
import { ReviewService } from '../../services/review.service';
import { SeoService } from '../../services/seo.service';
import { reviewAuthorName } from '../../utils/unit-seo.utils';
import { Review, UnitDetails } from '../../models/unit.model';
import { Observable, switchMap, map, of, tap } from 'rxjs';
import { AddReviewComponent } from '../add-review/add-review.component';

/**
 * This component shows all the details for a single unit, including its reviews.
 * Beginners: We use 'ActivatedRoute' to get the unit code from the browser's URL.
 */
@Component({
  selector: 'app-unit-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, AddReviewComponent],
  templateUrl: './unit-detail.component.html',
  styleUrl: './unit-detail.component.css'
})
export class UnitDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private unitService = inject(UnitService);
  private reviewService = inject(ReviewService);
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

  ngOnInit(): void {
    this.loadUnit();
  }

  loadUnit() {
    // 1. Listen to changes in the URL parameters (like /units/COMP1000)
    // 2. Use 'switchMap' to switch from the URL stream to the API data stream
    this.unit$ = this.route.paramMap.pipe(
      switchMap(params => {
        const code = params.get('code') || '';
        if (!code) return of(null as any);
        return this.unitService.getUnitByCode(code);
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
        }
      })
    );
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
}
