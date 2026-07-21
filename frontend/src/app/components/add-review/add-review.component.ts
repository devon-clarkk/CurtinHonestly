import { Component, inject, input, output, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CampaignProgress, CreateReviewResponse, ReviewService } from '../../services/review.service';
import { generateSemesterOptions } from '../../utils/semester-options.util';
import { MyReview, REVIEW_TAGS } from '../../models/unit.model';

@Component({
  selector: 'app-add-review',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './add-review.component.html',
  styleUrl: './add-review.component.css'
})
export class AddReviewComponent implements OnInit {
  private reviewService = inject(ReviewService);

  unitCode = input.required<string>();
  // When set, the form edits this existing review (PUT) instead of creating
  // a new one (POST) — pre-filled from its current values.
  editReview = input<MyReview | null>(null);
  reviewAdded = output<void>();
  reviewError = output<string>();
  cancel = output<void>();

  // Generated from today's date instead of a hardcoded list — see quick-fixes.md #4.
  semesterOptions = generateSemesterOptions();

  rating = signal(5);
  reviewText = signal('');
  semesterTaken = signal(this.semesterOptions[0]);
  professor = signal('');
  workload = signal(5);
  hasExam = signal(false);
  wouldTakeAgain = signal(true);
  finalGrade = signal<number | null>(null);

  // Assessment/experience tags (review-experience.md #4) — predefined chips,
  // not free text, so the aggregate summary stays meaningful.
  reviewTags = REVIEW_TAGS;
  selectedTags = signal<Set<string>>(new Set());

  isLoading = signal(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  get isEditing(): boolean {
    return !!this.editReview();
  }

  ngOnInit() {
    const existing = this.editReview();
    if (!existing) {
      return;
    }
    this.rating.set(existing.rating);
    this.reviewText.set(existing.reviewText);
    this.semesterTaken.set(existing.semesterTaken || this.semesterOptions[0]);
    this.professor.set(existing.professor || '');
    this.workload.set(existing.workload ?? 5);
    this.hasExam.set(existing.hasExam ?? false);
    this.wouldTakeAgain.set(existing.wouldTakeAgain ?? true);
    this.finalGrade.set(existing.finalGrade ?? null);
    this.selectedTags.set(new Set(existing.tags ?? []));
  }

  isTagSelected(tag: string): boolean {
    return this.selectedTags().has(tag);
  }

  toggleTag(tag: string): void {
    const current = new Set(this.selectedTags());
    if (current.has(tag)) {
      current.delete(tag);
    } else {
      current.add(tag);
    }
    this.selectedTags.set(current);
  }

  onSubmit() {
    if (!this.reviewText() || this.reviewText().length < 10) {
      this.errorMessage.set('Please write at least 10 characters in your review.');
      return;
    }

    if (this.finalGrade() !== null && (this.finalGrade()! < 0 || this.finalGrade()! > 100)) {
      this.errorMessage.set('Final grade must be between 0 and 100%.');
      return;
    }

    // Profanity is enforced server-side (ProfanityFilterService) — no client-side
    // word list here (quick-fixes.md #5: avoid shipping the banned-word list in
    // the bundle). A rejection surfaces via the error handler below.

    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    const reviewData = {
      rating: this.rating(),
      reviewText: this.reviewText(),
      semesterTaken: this.semesterTaken(),
      professor: this.professor(),
      workload: this.workload(),
      hasExam: this.hasExam(),
      wouldTakeAgain: this.wouldTakeAgain(),
      finalGrade: this.finalGrade(),
      unitCode: this.unitCode(),
      tags: [...this.selectedTags()]
    };

    const editing = this.editReview();
    if (editing) {
      this.reviewService.updateReview(editing.id, reviewData).subscribe({
        next: () => {
          this.isLoading.set(false);
          this.successMessage.set('Review updated!');
          setTimeout(() => this.reviewAdded.emit(), 1000);
        },
        error: (err) => {
          this.isLoading.set(false);
          const message = err.error?.error || 'Failed to update review. Please try again.';
          this.errorMessage.set(message);
          this.reviewError.emit(message);
        }
      });
      return;
    }

    this.reviewService.createReview(reviewData).subscribe({
      next: (response: CreateReviewResponse) => {
        this.isLoading.set(false);
        this.handleSuccess(response);
      },
      error: (err) => {
        this.isLoading.set(false);
        const message = err.error?.error || 'Failed to submit review. Please try again.';
        this.errorMessage.set(message);
        this.reviewError.emit(message);
      }
    });
  }

  private handleSuccess(response: CreateReviewResponse) {
    const progressMessage = this.buildProgressMessage(response.campaignProgress);

    if (response.campaignEntryToken) {
      this.successMessage.set(
        `Review submitted! You're entered in the ${response.campaignName ?? 'campaign'} draw. Entry token: ${response.campaignEntryToken}`
      );
      setTimeout(() => {
        this.reviewAdded.emit();
        this.resetForm();
      }, 4000);
      return;
    }

    if (progressMessage) {
      this.successMessage.set(`Review submitted! ${progressMessage}`);
      setTimeout(() => {
        this.reviewAdded.emit();
        this.resetForm();
      }, 3000);
      return;
    }

    this.reviewAdded.emit();
    this.resetForm();
  }

  private buildProgressMessage(progress: CampaignProgress | null): string | null {
    if (!progress) {
      return null;
    }

    if (progress.requireVerifiedStudent && progress.entriesEarned === 0 && progress.qualifyingReviews > 0) {
      return 'Verify your student email to earn draw entries.';
    }

    if (progress.minLikesGiven > 0 && progress.likesGiven < progress.minLikesGiven) {
      const needed = progress.minLikesGiven - progress.likesGiven;
      return `Mark ${needed} more review${needed === 1 ? '' : 's'} as helpful to unlock draw entries (${progress.likesGiven}/${progress.minLikesGiven}).`;
    }

    if (progress.entriesEarned >= progress.maxEntries) {
      return 'You have earned the maximum draw entries for this campaign.';
    }

    if (progress.minLikesReceived > 0 && progress.qualifyingReviews === 0) {
      return `Your review needs at least ${progress.minLikesReceived} helpful mark${progress.minLikesReceived === 1 ? '' : 's'} before it counts toward a draw entry.`;
    }

    const remainder = progress.qualifyingReviews % progress.requiredReviews;
    if (remainder !== 0) {
      const needed = progress.requiredReviews - remainder;
      return `${needed} more qualifying review${needed === 1 ? '' : 's'} needed for a draw entry (${progress.qualifyingReviews}/${progress.requiredReviews}).`;
    }

    return null;
  }

  resetForm() {
    this.rating.set(5);
    this.reviewText.set('');
    this.semesterTaken.set(this.semesterOptions[0]);
    this.professor.set('');
    this.workload.set(5);
    this.hasExam.set(false);
    this.wouldTakeAgain.set(true);
    this.finalGrade.set(null);
    this.selectedTags.set(new Set());
    this.successMessage.set(null);
  }

  onCancel() {
    this.cancel.emit();
  }
}
