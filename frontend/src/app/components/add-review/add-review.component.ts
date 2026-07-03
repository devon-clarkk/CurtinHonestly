import { Component, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CampaignProgress, CreateReviewResponse, ReviewService } from '../../services/review.service';
import { BANNED_WORDS } from '../../models/profanity-list';
import { MyReview } from '../../models/unit.model';

@Component({
  selector: 'app-add-review',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './add-review.component.html',
  styleUrl: './add-review.component.css'
})
export class AddReviewComponent {
  private reviewService = inject(ReviewService);

  unitCode = input.required<string>();
  editReview = input<MyReview | null>(null);
  reviewAdded = output<void>();
  cancel = output<void>();

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
  successMessage = signal<string | null>(null);

  private readonly profanityRegex = new RegExp(
    `\\b(${BANNED_WORDS.map(word => this.escapeRegExp(word)).join('|')})\\b`,
    'i'
  );

  constructor() {
    effect(() => {
      const existing = this.editReview();
      if (!existing) {
        return;
      }
      this.rating.set(existing.rating);
      this.reviewText.set(existing.reviewText);
      this.semesterTaken.set(existing.semesterTaken);
      this.professor.set(existing.professor ?? '');
      this.workload.set(existing.workload ?? 5);
      this.hasExam.set(existing.hasExam ?? false);
      this.wouldTakeAgain.set(existing.wouldTakeAgain ?? true);
      this.finalGrade.set(existing.finalGrade ?? null);
    });
  }

  isEditing(): boolean {
    return !!this.editReview()?.id;
  }

  private escapeRegExp(string: string) {
    return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  private containsProfanity(text: string): boolean {
    if (!text) return false;
    return this.profanityRegex.test(text);
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

    if (this.containsProfanity(this.reviewText())) {
      this.errorMessage.set('Your review contains language that violates our community standards. Please keep it professional.');
      return;
    }

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
      unit: { code: this.unitCode() }
    };

    const request = this.isEditing()
      ? this.reviewService.updateReview(this.editReview()!.id, reviewData)
      : this.reviewService.createReview(reviewData);

    request.subscribe({
      next: (response: CreateReviewResponse) => {
        this.isLoading.set(false);
        this.handleSuccess(response);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.error || 'Failed to submit review. Please try again.');
      }
    });
  }

  private handleSuccess(response: CreateReviewResponse) {
    const progressMessage = this.buildProgressMessage(response.campaignProgress);

    if (response.campaignEntryToken) {
      this.successMessage.set(
        `Review ${this.isEditing() ? 'updated' : 'submitted'}! You're entered in the ${response.campaignName ?? 'campaign'} draw. Entry token: ${response.campaignEntryToken}`
      );
      setTimeout(() => {
        this.reviewAdded.emit();
        if (!this.isEditing()) {
          this.resetForm();
        }
      }, 4000);
      return;
    }

    if (progressMessage) {
      this.successMessage.set(`Review ${this.isEditing() ? 'updated' : 'submitted'}! ${progressMessage}`);
      setTimeout(() => {
        this.reviewAdded.emit();
        if (!this.isEditing()) {
          this.resetForm();
        }
      }, 3000);
      return;
    }

    this.reviewAdded.emit();
    if (!this.isEditing()) {
      this.resetForm();
    }
  }

  private buildProgressMessage(progress: CampaignProgress | null): string | null {
    if (!progress) {
      return null;
    }

    if (progress.requireVerifiedStudent && progress.entriesEarned === 0 && progress.qualifyingReviews > 0) {
      return 'Verify your student email to earn draw entries.';
    }

    if (progress.entriesEarned >= progress.maxEntries) {
      return 'You have earned the maximum draw entries for this campaign.';
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
    this.semesterTaken.set('Semester 1, 2026');
    this.professor.set('');
    this.workload.set(5);
    this.hasExam.set(false);
    this.wouldTakeAgain.set(true);
    this.finalGrade.set(null);
    this.successMessage.set(null);
  }

  onCancel() {
    this.cancel.emit();
  }
}
