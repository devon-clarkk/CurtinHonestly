import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UnitService } from '../../services/unit.service';
import { AuthService } from '../../services/auth.service';
import { ReviewService } from '../../services/review.service';
import { SeoService } from '../../services/seo.service';
import { MyReview, UnitDetails } from '../../models/unit.model';
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

  unit$: Observable<UnitDetails> | undefined;
  showAddReviewForm = signal(false);
  myReviewForUnit = signal<MyReview | null>(null);

  ngOnInit(): void {
    this.loadUnit();
  }

  loadUnit() {
    this.unit$ = this.route.paramMap.pipe(
      switchMap(params => {
        const code = params.get('code') || '';
        if (!code) return of(null as any);
        this.loadMyReviewForUnit(code);
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
    this.showAddReviewForm.update(v => !v);
  }

  onReviewAdded() {
    this.showAddReviewForm.set(false);
    const code = this.route.snapshot.paramMap.get('code');
    if (code) {
      this.loadMyReviewForUnit(code);
    }
    this.loadUnit();
  }

  hasExistingReview(): boolean {
    return !!this.myReviewForUnit();
  }

  private loadMyReviewForUnit(unitCode: string) {
    if (!this.authService.isLoggedIn()) {
      this.myReviewForUnit.set(null);
      return;
    }

    this.reviewService.getMyReviewForUnit(unitCode).subscribe({
      next: (review) => this.myReviewForUnit.set(review),
      error: () => this.myReviewForUnit.set(null)
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
