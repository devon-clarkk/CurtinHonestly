import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { SeoService } from '../../services/seo.service';

type ConfirmState = 'ready' | 'submitting' | 'success' | 'error';

@Component({
  selector: 'app-verify-student-confirm',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="confirm-wrap">
      @if (state() === 'ready' || state() === 'submitting') {
        <h1>Confirm your student email</h1>
        <p>Press the button to finish verifying your Curtin student email and add the
          <strong>Verified Curtin Student</strong> badge to your account.</p>
        <button type="button" class="btn" (click)="confirm()" [disabled]="state() === 'submitting'">
          {{ state() === 'submitting' ? 'Confirming…' : 'Confirm my student email' }}
        </button>
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
           text-decoration: none; border: 0; font: inherit; cursor: pointer; }
    .btn:disabled { opacity: 0.6; cursor: default; }
  `]
})
export class VerifyStudentConfirmComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(AuthService);
  private seoService = inject(SeoService);

  state = signal<ConfirmState>('ready');
  errorMessage = signal<string>('This verification link could not be confirmed.');

  // Held in memory only, between reading it from the URL and posting it. It is
  // deliberately not a signal or anything else that ends up in the template.
  private token: string | null = null;

  ngOnInit() {
    this.seoService.noIndex('Verify student email | CurtinHonestly');

    this.token = this.route.snapshot.queryParamMap.get('token');
    if (!this.token) {
      this.state.set('error');
      this.errorMessage.set('No verification token was provided.');
      return;
    }

    // Drop the token from the address bar as soon as we've read it (security audit
    // finding #5). The emailed link has to carry it in a URL, but nothing after this
    // point does: leaving it there puts a live single-use credential into browser
    // history and into the Referer of any request this page later makes.
    //
    // Router.navigate, not history.replaceState. This route resolves to the '**'
    // RenderMode.Client entry in app.routes.server.ts today, so ngOnInit happens in
    // the browser, but the router-aware call keeps working if that ever changes,
    // where a bare window.history reference would not. replaceUrl keeps the token
    // out of history rather than pushing a second entry that still holds it.
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {},
      replaceUrl: true
    });

    // Nothing is posted until the person presses the button. Student mailboxes sit
    // behind link scanners (Microsoft Safe Links on Curtin's Microsoft 365) that
    // open every emailed URL in a headless browser, JavaScript included, before the
    // recipient sees the message. A page that confirmed on load handed the scanner
    // the single-use token, and the person who then clicked was told the link had
    // already been used. A click is the one thing those scanners do not perform.
  }

  confirm() {
    if (!this.token || this.state() === 'submitting') {
      return;
    }
    this.state.set('submitting');

    this.authService.confirmStudentVerification(this.token).subscribe({
      next: () => {
        this.token = null;
        this.state.set('success');
        // Refresh cached email/verified state (the account email may have switched).
        this.authService.refreshAccountStatus().subscribe({ error: () => {} });
      },
      error: (err) => {
        this.state.set('error');
        // ErrorResponse on the backend is { error: string }; MessageResponse-shaped
        // bodies carry { message }. Check both before falling back to generic copy.
        this.errorMessage.set(
          err.error?.error || err.error?.message || 'This verification link could not be confirmed.');
      }
    });
  }
}
