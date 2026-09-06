import { Component, inject, PLATFORM_ID, signal } from '@angular/core';
import { RouterOutlet, RouterLink, Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FACULTY_HUBS } from './utils/faculty.util';
import { filter } from 'rxjs';
import { AuthService } from './services/auth.service';
import { ReferralTrackingService } from './services/referral-tracking.service';
import { ClubEventService } from './services/club-event.service';
import { environment } from '../environments/environment';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  /** Linked from the footer, so every prerendered page reaches the hubs. */
  readonly facultyHubs = FACULTY_HUBS;

  protected readonly title = signal('Curtin Honestly');
  /** Build-time flag (BOARDS_ENABLED): the Boards nav entry exists only in builds with boards. */
  readonly boardsEnabled = environment.boardsEnabled;
  /** Build-time flag (PERSONAL_RECS_ENABLED): the For you nav entry exists only in builds with personal recommendations. */
  readonly personalRecsEnabled = environment.personalRecsEnabled;
  /** True once the browser has confirmed there is at least one upcoming event; the Events nav entry follows it. */
  readonly hasEvents = signal(false);
  authService = inject(AuthService);
  private clubEvents = inject(ClubEventService);
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

      // The Events nav entry appears only when there is something to see. One
      // browser-side request after hydration; the prerendered header has no
      // Events link, and any error leaves it that way.
      this.clubEvents.hasUpcoming().subscribe({
        next: (has) => this.hasEvents.set(has),
        error: () => this.hasEvents.set(false)
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
