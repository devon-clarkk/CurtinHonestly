import { Component, OnInit, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ClubEventService } from '../../../services/club-event.service';
import { SeoService } from '../../../services/seo.service';
import { ClubEvent } from '../../../models/club-event.model';
import { formatPerthDateTime, formatPerthRange } from '../../../utils/perth-time.util';

/**
 * /events/:id: one event in full, with a view beacon so clubs can see what
 * students open. Client-rendered and noindex for now.
 */
@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './event-detail.component.html',
  styleUrls: ['../events.css', './event-detail.component.css']
})
export class EventDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private eventService = inject(ClubEventService);
  private seoService = inject(SeoService);
  private platformId = inject(PLATFORM_ID);

  event = signal<ClubEvent | null>(null);
  loading = signal(true);
  notFound = signal(false);

  ngOnInit(): void {
    this.seoService.noIndex('Event | CurtinHonestly');
    this.route.paramMap.subscribe((params) => this.load(params.get('id') ?? ''));
  }

  private load(id: string): void {
    this.loading.set(true);
    this.notFound.set(false);
    this.event.set(null);
    if (!id) {
      this.loading.set(false);
      this.notFound.set(true);
      return;
    }
    this.eventService.get(id).subscribe({
      next: (event) => {
        this.event.set(event);
        this.loading.set(false);
        this.seoService.noIndex(`${event.title} | ${event.clubName} | CurtinHonestly`);
        if (isPlatformBrowser(this.platformId)) {
          this.eventService.recordView(event.id).subscribe({ next: () => undefined, error: () => undefined });
        }
      },
      error: () => {
        this.loading.set(false);
        this.notFound.set(true);
      }
    });
  }

  /** The first (or only) date, as a full range with the year. */
  firstWhen(event: ClubEvent): string {
    return event.endsAt ? formatPerthRange(event.startsAt, event.endsAt) : formatPerthDateTime(event.startsAt, true);
  }

  nextWhen(event: ClubEvent): string {
    return formatPerthDateTime(event.nextStartsAt);
  }

  showsNext(event: ClubEvent): boolean {
    return event.recurring && event.nextStartsAt !== event.startsAt;
  }

  where(event: ClubEvent): string {
    if (event.location && event.online) {
      return `${event.location} and online`;
    }
    if (event.location) {
      return event.location;
    }
    return event.online ? 'Online' : 'Location to be announced';
  }

  /** Description paragraphs, split on blank lines so the text keeps its shape. */
  paragraphs(event: ClubEvent): string[] {
    return (event.description ?? '')
      .split(/\n\s*\n/)
      .map((p) => p.trim())
      .filter((p) => p.length > 0);
  }
}
