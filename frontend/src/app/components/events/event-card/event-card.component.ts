import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ClubEvent } from '../../../models/club-event.model';
import { formatPerthDateTime, formatPerthRange, formatPerthTime } from '../../../utils/perth-time.util';

/**
 * One event as a compact card: club, kind, title, Perth-local time, place and
 * link. Used by the home strip, the unit page card, the /events listing and
 * club profiles, so every surface shows the same shape.
 *
 * Usage: <app-event-card [event]="event" [showClub]="true" />
 */
@Component({
  selector: 'app-event-card',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './event-card.component.html',
  styleUrl: './event-card.component.css'
})
export class EventCardComponent {
  @Input({ required: true }) event!: ClubEvent;
  /** Hide the club line when the card already sits under the club's own heading. */
  @Input() showClub = true;

  /**
   * "Tue 8 Sep, 4:00 pm to 5:00 pm". A recurring event whose first date has
   * passed shows its projected next occurrence, with the end shifted by the
   * same amount so the range still reads as one session.
   */
  when(): string {
    const e = this.event;
    if (!e.endsAt) {
      return formatPerthDateTime(e.nextStartsAt);
    }
    if (e.nextStartsAt === e.startsAt) {
      return formatPerthRange(e.startsAt, e.endsAt);
    }
    const shift = new Date(e.nextStartsAt).getTime() - new Date(e.startsAt).getTime();
    const end = new Date(new Date(e.endsAt).getTime() + shift);
    const endsSameDay = formatPerthDateTime(e.nextStartsAt).split(',')[0] === formatPerthDateTime(end).split(',')[0];
    return endsSameDay
      ? `${formatPerthDateTime(e.nextStartsAt)} to ${formatPerthTime(end)}`
      : formatPerthRange(e.nextStartsAt, end);
  }

  /** Where it happens: the room, "Online", or both when a room also streams. */
  where(): string {
    const e = this.event;
    if (e.location && e.online) {
      return `${e.location} and online`;
    }
    if (e.location) {
      return e.location;
    }
    return e.online ? 'Online' : 'Location to be announced';
  }

  linkLabel(): string {
    return this.event.online && !this.event.location ? 'Join online' : 'Details and registration';
  }
}
