import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ClubEventService } from '../../../services/club-event.service';
import { SeoService } from '../../../services/seo.service';
import { ClubProfile } from '../../../models/club-event.model';
import { EventCardComponent } from '../../events/event-card/event-card.component';

/**
 * /clubs/:slug: a club's profile and its upcoming events. Client-rendered and
 * noindex for now.
 */
@Component({
  selector: 'app-club-profile',
  standalone: true,
  imports: [RouterLink, EventCardComponent],
  templateUrl: './club-profile.component.html',
  styleUrls: ['../../events/events.css', './club-profile.component.css']
})
export class ClubProfileComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private eventService = inject(ClubEventService);
  private seoService = inject(SeoService);

  club = signal<ClubProfile | null>(null);
  loading = signal(true);
  notFound = signal(false);

  ngOnInit(): void {
    this.seoService.noIndex('Club | CurtinHonestly');
    this.route.paramMap.subscribe((params) => this.load(params.get('slug') ?? ''));
  }

  private load(slug: string): void {
    this.loading.set(true);
    this.notFound.set(false);
    this.club.set(null);
    if (!slug) {
      this.loading.set(false);
      this.notFound.set(true);
      return;
    }
    this.eventService.club(slug).subscribe({
      next: (club) => {
        this.club.set(club);
        this.loading.set(false);
        this.seoService.noIndex(`${club.name} | CurtinHonestly`);
      },
      error: () => {
        this.loading.set(false);
        this.notFound.set(true);
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
