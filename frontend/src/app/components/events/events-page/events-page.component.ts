import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ClubEventService } from '../../../services/club-event.service';
import { SeoService } from '../../../services/seo.service';
import { AuthService } from '../../../services/auth.service';
import {
  CLUB_EVENT_KINDS,
  CLUB_EVENT_KIND_LABELS,
  ClubEvent,
  ClubEventKind,
  ClubSummary
} from '../../../models/club-event.model';
import { perthWeekLabel, perthWeekStartKey } from '../../../utils/perth-time.util';
import { EventCardComponent } from '../event-card/event-card.component';

const PAGE_SIZE = 48;

interface WeekGroup {
  key: string;
  label: string;
  events: ClubEvent[];
}

/**
 * /events: every upcoming club event, filterable by club and kind, grouped by
 * Perth week. Client-rendered and noindex for now, like the boards.
 */
@Component({
  selector: 'app-events-page',
  standalone: true,
  imports: [RouterLink, EventCardComponent],
  templateUrl: './events-page.component.html',
  styleUrls: ['../events.css', './events-page.component.css']
})
export class EventsPageComponent implements OnInit {
  private eventService = inject(ClubEventService);
  private seoService = inject(SeoService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  authService = inject(AuthService);

  readonly kinds = CLUB_EVENT_KINDS;
  readonly kindLabels = CLUB_EVENT_KIND_LABELS;

  clubs = signal<ClubSummary[]>([]);
  events = signal<ClubEvent[]>([]);
  page = signal(0);
  totalPages = signal(0);
  total = signal(0);
  loading = signal(true);
  errorMessage = signal<string | null>(null);

  clubSlug = signal<string>('');
  kind = signal<ClubEventKind | ''>('');

  /** Events on the current page bucketed by the Perth week they fall in, in order. */
  weeks = computed<WeekGroup[]>(() => {
    const now = new Date();
    const groups = new Map<string, WeekGroup>();
    for (const event of this.events()) {
      const key = perthWeekStartKey(event.nextStartsAt);
      let group = groups.get(key);
      if (!group) {
        group = { key, label: perthWeekLabel(key, now), events: [] };
        groups.set(key, group);
      }
      group.events.push(event);
    }
    return [...groups.values()];
  });

  ngOnInit(): void {
    this.seoService.noIndex('Study sessions and events | CurtinHonestly');
    const params = this.route.snapshot.queryParamMap;
    this.clubSlug.set(params.get('club') ?? '');
    const kind = params.get('kind') ?? '';
    this.kind.set(this.kinds.includes(kind as ClubEventKind) ? (kind as ClubEventKind) : '');

    this.eventService.clubs().subscribe({
      next: (clubs) => this.clubs.set(clubs),
      error: () => this.clubs.set([])
    });
    this.load(0);
  }

  load(page: number): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.eventService.list(page, PAGE_SIZE, this.clubSlug() || null, this.kind() || null).subscribe({
      next: (result) => {
        this.events.set(result.content);
        this.page.set(result.number);
        this.totalPages.set(result.totalPages);
        this.total.set(result.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Events could not be loaded. Please try again shortly.');
        this.loading.set(false);
      }
    });
  }

  onClubChange(value: string): void {
    this.clubSlug.set(value);
    this.syncQuery();
    this.load(0);
  }

  onKindChange(value: string): void {
    this.kind.set(this.kinds.includes(value as ClubEventKind) ? (value as ClubEventKind) : '');
    this.syncQuery();
    this.load(0);
  }

  clearFilters(): void {
    this.clubSlug.set('');
    this.kind.set('');
    this.syncQuery();
    this.load(0);
  }

  hasFilters(): boolean {
    return !!this.clubSlug() || !!this.kind();
  }

  previousPage(): void {
    if (this.page() > 0) {
      this.load(this.page() - 1);
    }
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.load(this.page() + 1);
    }
  }

  /** Keeps the filters in the URL so a filtered view can be shared. */
  private syncQuery(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { club: this.clubSlug() || null, kind: this.kind() || null },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }
}
