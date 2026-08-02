import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { SeoService } from '../../services/seo.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <section class="auth-wrap">
      <h1>Reset your password</h1>
      @if (sent()) {
        <p>{{ message() }}</p>
        <a routerLink="/login" class="btn">Back to login</a>
      } @else {
        <p>Enter your account email and we'll send you a reset link.</p>
        <form (ngSubmit)="onSubmit()" #f="ngForm">
          <input type="email" name="email" [(ngModel)]="email" required placeholder="you@example.com"
                 class="field" />
          @if (error()) { <p class="error">{{ error() }}</p> }
          <button type="submit" [disabled]="!f.valid || loading()" class="btn">
            {{ loading() ? 'Sending…' : 'Send reset link' }}
          </button>
        </form>
        <a routerLink="/login" class="link">Back to login</a>
      }
    </section>
  `,
  styles: [`
    .auth-wrap { max-width: 26rem; margin: 4rem auto; padding: 0 1rem; text-align: center; }
    .field { width: 100%; padding: 0.6rem; margin: 0.75rem 0; border: 1px solid #ccc; border-radius: 6px; box-sizing: border-box; }
    .btn { display: inline-block; margin-top: 0.5rem; padding: 0.6rem 1.2rem; background: var(--color-primary, #0a2540);
           color: #fff; border: none; border-radius: 6px; text-decoration: none; cursor: pointer; }
    .btn:disabled { opacity: 0.6; cursor: default; }
    .link { display: inline-block; margin-top: 1rem; }
    .error { color: #c0392b; }
  `]
})
export class ForgotPasswordComponent {
  private authService = inject(AuthService);
  private seoService = inject(SeoService);

  email = '';
  loading = signal(false);
  sent = signal(false);
  message = signal('');
  error = signal<string | null>(null);

  constructor() {
    this.seoService.noIndex('Reset password | CurtinHonestly');
  }

  onSubmit() {
    this.error.set(null);
    this.loading.set(true);
    this.authService.forgotPassword(this.email).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.message.set(res.message
          || "If an account exists for that email, we've sent a password reset link.");
        this.sent.set(true);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.error || 'Something went wrong. Please try again.');
      }
    });
  }
}
