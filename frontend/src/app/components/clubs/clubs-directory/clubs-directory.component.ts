import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ClubEventService } from '../../../services/club-event.service';
import { SeoService } from '../../../services/seo.service';
import { AuthService } from '../../../services/auth.service';
import { ClubSummary } from '../../../models/club-event.model';

/**
 * /clubs: every active club with how many sessions it has coming up, and the
 * pitch to clubs that are not listed yet. Client-rendered and noindex for now.
 */
@Component({
  selector: 'app-clubs-directory',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './clubs-directory.component.html',
  styleUrls: ['../../events/events.css', './clubs-directory.component.css']
})
export class ClubsDirectoryComponent implements OnInit {
  private eventService = inject(ClubEventService);
  private seoService = inject(SeoService);
  authService = inject(AuthService);

  clubs = signal<ClubSummary[]>([]);
  loading = signal(true);
  errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.seoService.noIndex('Clubs and study services | CurtinHonestly');
    this.eventService.clubs().subscribe({
      next: (clubs) => {
        this.clubs.set(clubs);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Clubs could not be loaded. Please try again shortly.');
        this.loading.set(false);
      }
    });
  }

  initials(name: string): string {
    const words = name.split(/\s+/).filter((w) => w.length > 0);
    if (words.length === 0) {
      return '?';
    }
    if (words.length === 1) {
      return words[0].slice(0, 2).toUpperCase();
    }
    return (words[0][0] + words[words.length - 1][0]).toUpperCase();
  }

  host(url: string): string {
    try {
      return new URL(url).hostname.replace(/^www\./, '');
    } catch {
      return url;
    }
  }
}
