import { Component, Input, OnChanges, PLATFORM_ID, SimpleChanges, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ClubEventService } from '../../../services/club-event.service';
import { ClubEvent } from '../../../models/club-event.model';
import { EventCardComponent } from '../event-card/event-card.component';

/**
 * "Study sessions and events for {code}" card on a unit page: the upcoming
 * club events whose targeting covers this unit.
 *
 * Fetches in the browser only. Unit pages are prerendered in bulk and event
 * dates move by the week, so nothing here is baked into the static HTML; the
 * card renders nothing at all until there is something to show.
 *
 * Usage: <app-unit-events-card [unitCode]="unit.code" />
 */
@Component({
  selector: 'app-unit-events-card',
  standalone: true,
  imports: [RouterLink, EventCardComponent],
  templateUrl: './unit-events-card.component.html',
  styleUrl: './unit-events-card.component.css'
})
export class UnitEventsCardComponent implements OnChanges {
  @Input({ required: true }) unitCode!: string;

  private eventService = inject(ClubEventService);
  private platformId = inject(PLATFORM_ID);

  events = signal<ClubEvent[]>([]);

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['unitCode']) {
      return;
    }
    this.events.set([]);
    const code = (this.unitCode ?? '').trim();
    if (!code || !isPlatformBrowser(this.platformId)) {
      return;
    }
    this.eventService.forUnit(code).subscribe({
      next: (events) => this.events.set(events ?? []),
      error: () => this.events.set([])
    });
  }
}
