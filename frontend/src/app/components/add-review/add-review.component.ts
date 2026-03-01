import { Component, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReviewService } from '../../services/review.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-add-review',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './add-review.component.html',
  styleUrl: './add-review.component.css'
})
export class AddReviewComponent {
  private reviewService = inject(ReviewService);
  private router = inject(Router);

  unitCode = input.required<string>();
  reviewAdded = output<void>();
  cancel = output<void>();

  // Form fields
  rating = signal(5);
  reviewText = signal('');
  semesterTaken = signal('Semester 1, 2026');
  professor = signal('');
  workload = signal(5);
  hasExam = signal(false);
  wouldTakeAgain = signal(true);
  finalGrade = signal<number | null>(null);

  isLoading = signal(false);
  errorMessage = signal<string | null>(null);

  onSubmit() {
    if (!this.reviewText() || this.reviewText().length < 10) {
      this.errorMessage.set('Please write at least 10 characters in your review.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const reviewData = {
      rating: this.rating(),
      reviewText: this.reviewText(),
      semesterTaken: this.semesterTaken(),
      professor: this.professor(),
      workload: this.workload(),
      hasExam: this.hasExam(),
      wouldTakeAgain: this.wouldTakeAgain(),
      finalGrade: this.finalGrade(),
      unit: { code: this.unitCode() }
    };

    this.reviewService.createReview(reviewData).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.reviewAdded.emit();
        // Reset form
        this.resetForm();
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.error || 'Failed to submit review. Please try again.');
      }
    });
  }

  resetForm() {
    this.rating.set(5);
    this.reviewText.set('');
    this.semesterTaken.set('Semester 1, 2026');
    this.professor.set('');
    this.workload.set(5);
    this.hasExam.set(false);
    this.wouldTakeAgain.set(true);
    this.finalGrade.set(null);
  }

  onCancel() {
    this.cancel.emit();
  }
}
