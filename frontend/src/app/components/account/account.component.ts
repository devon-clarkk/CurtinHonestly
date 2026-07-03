import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService, AccountStatus } from '../../services/auth.service';

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

  account = signal<AccountStatus | null>(null);

  newEmail = '';
  emailPassword = '';
  studentEmail = '';
  verifyPassword = '';
  deletePassword = '';

  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);
  isLoading = signal(false);

  ngOnInit() {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }

    this.authService.refreshAccountStatus().subscribe({
      next: (status) => this.account.set(status),
      error: () => this.authService.logout()
    });
  }

  campaignProgressLabel(): string | null {
    const progress = this.account()?.campaignProgress;
    if (!progress) {
      return null;
    }

    if (progress.entriesEarned >= progress.maxEntries) {
      return `You have ${progress.entriesEarned} draw ${progress.entriesEarned === 1 ? 'entry' : 'entries'}.`;
    }

    const remainder = progress.qualifyingReviews % progress.requiredReviews;
    if (remainder === 0 && progress.qualifyingReviews === 0) {
      return `Leave ${progress.requiredReviews} qualifying review${progress.requiredReviews === 1 ? '' : 's'} on different units to enter the draw.`;
    }

    if (remainder === 0) {
      return `${progress.qualifyingReviews} qualifying reviews submitted.`;
    }

    const needed = progress.requiredReviews - remainder;
    return `${progress.qualifyingReviews}/${progress.requiredReviews} qualifying reviews — ${needed} more needed for a draw entry.`;
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
      this.errorMessage.set('Please enter a valid @student.curtin.edu.au email.');
      return;
    }

    this.clearMessages();
    this.isLoading.set(true);

    this.authService.verifyStudent({
      studentEmail: this.studentEmail,
      password: this.verifyPassword
    }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.successMessage.set('Your account is now verified as a Curtin student.');
        this.studentEmail = '';
        this.verifyPassword = '';
        this.refreshAccount();
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.error || 'Verification failed. Please try again.');
      }
    });
  }

  onDeleteAccount() {
    if (!confirm('Delete your account permanently? All your reviews will be removed. This cannot be undone.')) {
      return;
    }

    this.clearMessages();
    this.isLoading.set(true);

    this.authService.deleteAccount(this.deletePassword).subscribe({
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
  }
}
