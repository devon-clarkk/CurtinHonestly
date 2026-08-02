import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { SeoService } from '../../services/seo.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <section class="auth-wrap">
      <h1>Choose a new password</h1>
      @if (!token) {
        <p class="error">This reset link is missing its token. Request a new one.</p>
        <a routerLink="/forgot-password" class="btn">Request a new link</a>
      } @else if (done()) {
        <p>{{ message() }}</p>
        <a routerLink="/login" class="btn">Go to login</a>
      } @else {
        <form (ngSubmit)="onSubmit()" #f="ngForm">
          <input type="password" name="pw" [(ngModel)]="password" required minlength="8"
                 placeholder="New password (min 8 chars)" class="field" />
          <input type="password" name="pw2" [(ngModel)]="confirm" required
                 placeholder="Confirm new password" class="field" />
          @if (error()) { <p class="error">{{ error() }}</p> }
          <button type="submit" [disabled]="!f.valid || loading()" class="btn">
            {{ loading() ? 'Resetting…' : 'Reset password' }}
          </button>
        </form>
      }
    </section>
  `,
  styles: [`
    .auth-wrap { max-width: 26rem; margin: 4rem auto; padding: 0 1rem; text-align: center; }
    .field { width: 100%; padding: 0.6rem; margin: 0.5rem 0; border: 1px solid #ccc; border-radius: 6px; box-sizing: border-box; }
    .btn { display: inline-block; margin-top: 0.5rem; padding: 0.6rem 1.2rem; background: var(--color-primary, #0a2540);
           color: #fff; border: none; border-radius: 6px; text-decoration: none; cursor: pointer; }
    .btn:disabled { opacity: 0.6; cursor: default; }
    .error { color: #c0392b; }
  `]
})
export class ResetPasswordComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private authService = inject(AuthService);
  private seoService = inject(SeoService);

  token: string | null = null;
  password = '';
  confirm = '';
  loading = signal(false);
  done = signal(false);
  message = signal('');
  error = signal<string | null>(null);

  ngOnInit() {
    this.seoService.noIndex('Choose a new password | CurtinHonestly');
    this.token = this.route.snapshot.queryParamMap.get('token');
  }

  onSubmit() {
    this.error.set(null);
    if (this.password.length < 8) {
      this.error.set('Password must be at least 8 characters.');
      return;
    }
    if (this.password !== this.confirm) {
      this.error.set('Passwords do not match.');
      return;
    }
    this.loading.set(true);
    this.authService.resetPassword(this.token!, this.password).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.message.set(res.message || 'Your password has been reset. You can now log in.');
        this.done.set(true);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.error || 'This reset link is invalid or has expired.');
      }
    });
  }
}
