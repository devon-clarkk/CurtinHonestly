import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { SeoService } from '../../services/seo.service';

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './account.component.html',
  styleUrl: './account.component.css'
})
export class AccountComponent implements OnInit {
  protected authService = inject(AuthService);
  private router = inject(Router);
  private seoService = inject(SeoService);

  newEmail = '';
  emailPassword = '';
  studentEmail = '';
  verifyPassword = '';
  deletePassword = '';

  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);
  isLoading = signal(false);

  ngOnInit() {
    this.seoService.noIndex('Account | CurtinHonestly');

    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }

    this.authService.refreshAccountStatus().subscribe({
      error: () => this.authService.logout()
    });
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

  private clearMessages() {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
