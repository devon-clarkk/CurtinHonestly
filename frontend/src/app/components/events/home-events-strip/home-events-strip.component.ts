import { Component, OnInit, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ClubEventService } from '../../../services/club-event.service';
import { ClubEvent } from '../../../models/club-event.model';
import { EventCardComponent } from '../event-card/event-card.component';

const HOME_LIMIT = 4;

/**
 * "Study sessions and events" strip on the home page: up to four upcoming
 * events that clubs have flagged for the home page, plus a link to /events.
 *
 * Browser only. The signal stays empty during prerender and through
 * hydration, so the server-drawn catalogue and its TransferState handoff in
 * unit-list are untouched; the strip appears once the request answers, and
 * nothing at all is rendered when there is nothing to show.
 *
 * Usage: <app-home-events-strip />
 */
@Component({
  selector: 'app-home-events-strip',
  standalone: true,
  imports: [RouterLink, EventCardComponent],
  templateUrl: './home-events-strip.component.html',
  styleUrl: './home-events-strip.component.css'
})
export class HomeEventsStripComponent implements OnInit {
  private eventService = inject(ClubEventService);
  private platformId = inject(PLATFORM_ID);

  events = signal<ClubEvent[]>([]);

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    this.eventService.upcoming(HOME_LIMIT).subscribe({
      next: (events) => this.events.set(events ?? []),
      error: () => this.events.set([])
    });
  }
}
