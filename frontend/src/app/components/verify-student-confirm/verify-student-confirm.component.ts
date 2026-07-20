import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { SeoService } from '../../services/seo.service';

type ConfirmState = 'loading' | 'success' | 'error';

@Component({
  selector: 'app-verify-student-confirm',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="confirm-wrap">
      @if (state() === 'loading') {
        <h1>Verifying your student email…</h1>
        <p>One moment while we confirm your link.</p>
      } @else if (state() === 'success') {
        <h1>You're verified! 🎉</h1>
        <p>Your account now shows the <strong>Verified Curtin Student</strong> badge.</p>
        <a routerLink="/account" class="btn">Go to my account</a>
      } @else {
        <h1>We couldn't verify that link</h1>
        <p>{{ errorMessage() }}</p>
        <a routerLink="/account" class="btn">Request a new link</a>
      }
    </section>
  `,
  styles: [`
    .confirm-wrap { max-width: 32rem; margin: 4rem auto; padding: 0 1rem; text-align: center; }
    .btn { display: inline-block; margin-top: 1.5rem; padding: 0.6rem 1.2rem;
           background: var(--color-primary, #0a2540); color: #fff; border-radius: 6px;
           text-decoration: none; }
  `]
})
export class VerifyStudentConfirmComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private authService = inject(AuthService);
  private seoService = inject(SeoService);

  state = signal<ConfirmState>('loading');
  errorMessage = signal<string>('This verification link is invalid or has expired.');

  ngOnInit() {
    this.seoService.noIndex('Verify student email | CurtinHonestly');

    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.state.set('error');
      this.errorMessage.set('No verification token was provided.');
      return;
    }

    this.authService.confirmStudentVerification(token).subscribe({
      next: () => {
        this.state.set('success');
        // Refresh cached email/verified state (the account email may have switched).
        this.authService.refreshAccountStatus().subscribe({ error: () => {} });
      },
      error: (err) => {
        this.state.set('error');
        this.errorMessage.set(
          err.error?.message || err.error?.error || 'This verification link is invalid or has expired.');
      }
    });
  }
}
