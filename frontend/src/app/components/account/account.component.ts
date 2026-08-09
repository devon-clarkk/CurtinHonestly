import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService, AccountStatus } from '../../services/auth.service';
import { SeoService } from '../../services/seo.service';

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe],
  templateUrl: './account.component.html',
  styleUrl: './account.component.css'
})
export class AccountComponent implements OnInit {
  protected authService = inject(AuthService);
  private router = inject(Router);
  private seoService = inject(SeoService);

  account = signal<AccountStatus | null>(null);

  newEmail = '';
  emailPassword = '';
  studentEmail = '';
  verifyPassword = '';
  deletePassword = '';

  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);
  // Verification feedback is shown next to the verify form (not at the page top)
  // so the "check your student email" confirmation sits with the action it follows.
  verifyMessage = signal<string | null>(null);
  verifyError = signal<string | null>(null);
  isLoading = signal(false);

  ngOnInit() {
    this.seoService.noIndex('Account | CurtinHonestly');

    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }

    this.authService.refreshAccountStatus().subscribe({
      next: (status) => this.account.set(status),
      error: () => this.authService.logout()
    });
  }

  // The account page is behind the auth guard, so it only ever runs in the
  // browser — document access here is safe (no SSR path).
  scrollToVerify() {
    document.getElementById('verify-student')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  campaignProgressLabel(): string | null {
    const progress = this.account()?.campaignProgress;
    if (!progress) {
      return null;
    }

    if (progress.entriesEarned >= progress.maxEntries) {
      return `You have ${progress.entriesEarned} draw ${progress.entriesEarned === 1 ? 'entry' : 'entries'}.`;
    }

    if (progress.minLikesGiven > 0 && progress.likesGiven < progress.minLikesGiven) {
      const needed = progress.minLikesGiven - progress.likesGiven;
      return `Mark ${needed} more review${needed === 1 ? '' : 's'} as helpful (${progress.likesGiven}/${progress.minLikesGiven}) to unlock draw entries.`;
    }

    if (progress.minLikesReceived > 0) {
      const remainder = progress.qualifyingReviews % progress.requiredReviews;
      if (remainder === 0 && progress.qualifyingReviews === 0) {
        return `Leave reviews that receive at least ${progress.minLikesReceived} helpful mark${progress.minLikesReceived === 1 ? '' : 's'} to enter the draw.`;
      }
    }

    const remainder = progress.qualifyingReviews % progress.requiredReviews;
    if (remainder === 0 && progress.qualifyingReviews === 0) {
      return `Leave ${progress.requiredReviews} qualifying review${progress.requiredReviews === 1 ? '' : 's'} on different units to enter the draw.`;
    }

    if (remainder === 0) {
      return `${progress.qualifyingReviews} qualifying reviews submitted.`;
    }

    const needed = progress.requiredReviews - remainder;
    return `${progress.qualifyingReviews}/${progress.requiredReviews} qualifying reviews. ${needed} more needed for a draw entry.`;
  }

  onUpdateEmail() {
    this.clearMessages();
    this.isLoading.set(true);

    this.authService.updateEmail({
      newEmail: this.newEmail,
      password: this.emailPassword
    }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.successMessage.set('Email updated successfully.');
        this.newEmail = '';
        this.emailPassword = '';
        this.refreshAccount();
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.error || 'Failed to update email.');
      }
    });
  }

  onVerify() {
    if (!this.studentEmail.endsWith('@student.curtin.edu.au')) {
      this.clearMessages();
      this.verifyError.set('Please enter a valid @student.curtin.edu.au email.');
      return;
    }

    this.clearMessages();
    this.isLoading.set(true);

    this.authService.verifyStudent({
      studentEmail: this.studentEmail,
      password: this.verifyPassword
    }).subscribe({
      next: (response) => {
        this.isLoading.set(false);
        this.verifyMessage.set(response.message
          || 'Check your student email for a confirmation link to finish verifying.');
        this.studentEmail = '';
        this.verifyPassword = '';
      },
      error: (err) => {
        this.isLoading.set(false);
        this.verifyError.set(err.error?.error || 'Verification failed. Please try again.');
      }
    });
  }

  // Default: reviews are kept (anonymized) — only the account identity is removed.
  deleteReviewsToo = false;

  onDeleteAccount() {
    const confirmMessage = this.deleteReviewsToo
      ? 'Delete your account AND permanently remove all your reviews? This cannot be undone.'
      : 'Delete your account? Your reviews will stay on the site, posted anonymously. This cannot be undone.';
    if (!confirm(confirmMessage)) {
      return;
    }

    this.clearMessages();
    this.isLoading.set(true);

    this.authService.deleteAccount(this.deletePassword, this.deleteReviewsToo).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.authService.logout();
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.error || 'Failed to delete account.');
      }
    });
  }

  private refreshAccount() {
    this.authService.refreshAccountStatus().subscribe({
      next: (status) => this.account.set(status)
    });
  }

  private clearMessages() {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.verifyMessage.set(null);
    this.verifyError.set(null);
  }
}
