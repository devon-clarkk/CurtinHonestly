import { Component, inject, PLATFORM_ID, signal } from '@angular/core';
import { RouterOutlet, RouterLink, Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { filter } from 'rxjs';
import { AuthService } from './services/auth.service';
import { ReferralTrackingService } from './services/referral-tracking.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('Curtin Honestly');
  authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private platformId = inject(PLATFORM_ID);
  private referralTracking = inject(ReferralTrackingService);
  mobileNavOpen = signal(false);

  constructor() {
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => this.mobileNavOpen.set(false));

    // Site-wide referral capture: query params live on the URL (not a single
    // route), so the root route sees ?ref= no matter which page the link points
    // at. Browser-only — recordVisit hits the API and must not run during SSR.
    if (isPlatformBrowser(this.platformId)) {
      this.route.queryParamMap.subscribe((params) => {
        this.referralTracking.capture(params.get('ref'));
      });
    }
  }

  toggleMobileNav() {
    this.mobileNavOpen.update((open) => !open);
  }

  closeMobileNav() {
    this.mobileNavOpen.set(false);
  }

  logout() {
    this.authService.logout();
    this.closeMobileNav();
  }
}
