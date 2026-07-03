import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { BehaviorSubject, debounceTime, distinctUntilChanged, map, Observable, switchMap } from 'rxjs';
import { ReviewService } from '../../services/review.service';
import { UnitService } from '../../services/unit.service';
import { SeoService } from '../../services/seo.service';
import { MyReview, UnitSummary } from '../../models/unit.model';
import { AddReviewComponent } from '../add-review/add-review.component';

@Component({
  selector: 'app-my-reviews',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AddReviewComponent],
  templateUrl: './my-reviews.component.html',
  styleUrl: './my-reviews.component.css'
})
export class MyReviewsComponent implements OnInit {
  private reviewService = inject(ReviewService);
  private unitService = inject(UnitService);
  private router = inject(Router);
  private seoService = inject(SeoService);

  reviews = signal<MyReview[]>([]);
  isLoading = signal(true);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  showAddFlow = signal(false);
  selectedUnitCode = signal<string | null>(null);
  selectedUnitName = signal<string | null>(null);

  searchQuery = '';
  private searchSubject = new BehaviorSubject<string>('');
  searchResults$: Observable<UnitSummary[]> | undefined;

  ngOnInit() {
    this.seoService.noIndex('My Reviews | CurtinHonestly');
    this.loadReviews();

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

  startAddReview() {
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
  }

  deleteReview(review: MyReview) {
    if (!confirm(`Delete your review for ${review.unitCode}?`)) {
      return;
    }

    this.reviewService.deleteReview(review.id).subscribe({
      next: () => {
        this.successMessage.set('Review deleted.');
        this.loadReviews();
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
