import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ClubEventService } from '../../../services/club-event.service';
import {
  CLUB_EVENT_KINDS,
  CLUB_EVENT_KIND_LABELS,
  CLUB_EVENT_LIMITS,
  ClubEventKind,
  ClubEventManage,
  ClubEventOptions,
  ClubEventPreview,
  ClubEventUpsert
} from '../../../models/club-event.model';
import { apiErrorMessage } from '../../boards/board-time.util';
import { fromPerthInputValue, toPerthInputValue } from '../../../utils/perth-time.util';

type TargetMode = 'rule' | 'unit';

interface EventForm {
  title: string;
  description: string;
  kind: ClubEventKind;
  /** datetime-local values in Perth time; converted to UTC on save. */
  startsAt: string;
  endsAt: string;
  online: boolean;
  location: string;
  link: string;
  recurring: boolean;
  recurrenceNote: string;
  targetMode: TargetMode;
  unitCode: string;
  codePrefixes: string;
  faculty: string;
  level: string;
  showOnHome: boolean;
}

function emptyForm(): EventForm {
  return {
    title: '',
    description: '',
    kind: 'REVISION_SESSION',
    startsAt: '',
    endsAt: '',
    online: false,
    location: '',
    link: '',
    recurring: false,
    recurrenceNote: '',
    targetMode: 'rule',
    unitCode: '',
    codePrefixes: '',
    faculty: '',
    level: '',
    showOnHome: false
  };
}

function formFrom(event: ClubEventManage): EventForm {
  return {
    title: event.title,
    description: event.description ?? '',
    kind: event.kind,
    startsAt: toPerthInputValue(event.startsAt),
    endsAt: toPerthInputValue(event.endsAt),
    online: event.online,
    location: event.location ?? '',
    link: event.link ?? '',
    recurring: event.recurring,
    recurrenceNote: event.recurrenceNote ?? '',
    targetMode: event.targetUnitCode ? 'unit' : 'rule',
    unitCode: event.targetUnitCode ?? '',
    codePrefixes: event.codePrefixes ?? '',
    faculty: event.faculty ?? '',
    level: event.level ?? '',
    showOnHome: event.showOnHome
  };
}

/**
 * Create or edit one event from the club portal. Times are typed as Perth
 * local and sent as UTC. Targeting mirrors the resources admin form: a rule
 * over code prefixes, faculty and level, or one unit by code, with a preview
 * of how many units a rule reaches.
 *
 * Usage: <app-event-editor [clubId]="id" [event]="eventOrNull" [options]="options" [trusted]="club.trusted"
 *                          (saved)="..." (cancelled)="..." />
 */
@Component({
  selector: 'app-event-editor',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './event-editor.component.html',
  styleUrl: './event-editor.component.css'
})
export class EventEditorComponent implements OnChanges {
  @Input({ required: true }) clubId!: string;
  /** Null creates a new event. */
  @Input() event: ClubEventManage | null = null;
  @Input() options: ClubEventOptions | null = null;
  @Input() trusted = false;
  @Output() saved = new EventEmitter<ClubEventManage>();
  @Output() cancelled = new EventEmitter<void>();

  private eventService = inject(ClubEventService);

  readonly kinds = CLUB_EVENT_KINDS;
  readonly kindLabels = CLUB_EVENT_KIND_LABELS;
  readonly limits = CLUB_EVENT_LIMITS;

  form: EventForm = emptyForm();
  saving = signal(false);
  errorMessage = signal<string | null>(null);
  preview = signal<ClubEventPreview | null>(null);
  previewLoading = signal(false);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['event']) {
      this.form = this.event ? formFrom(this.event) : emptyForm();
      this.preview.set(null);
      this.errorMessage.set(null);
    }
  }

  isNew(): boolean {
    return !this.event;
  }

  /** Whether the "Save and publish" button applies: new events and anything not already live or queued. */
  canPublishAfterSave(): boolean {
    const status = this.event?.status;
    return !status || status === 'DRAFT' || status === 'REJECTED' || status === 'CANCELLED';
  }

  runPreview(): void {
    this.errorMessage.set(null);
    this.previewLoading.set(true);
    const rule = this.form.targetMode === 'unit'
      ? { unitCode: this.form.unitCode.trim() }
      : { codePrefixes: this.form.codePrefixes.trim(), faculty: this.form.faculty, level: this.form.level };
    this.eventService.preview(this.clubId, rule).subscribe({
      next: (preview) => {
        this.previewLoading.set(false);
        this.preview.set(preview);
      },
      error: (err) => {
        this.previewLoading.set(false);
        this.preview.set(null);
        this.errorMessage.set(apiErrorMessage(err, 'The preview could not be loaded.'));
      }
    });
  }

  previewSummary(preview: ClubEventPreview): string {
    if (preview.matchCount === 0) {
      return 'Matches no units. Check the prefixes or unit code.';
    }
    const sample = preview.sampleCodes.join(', ');
    const more = preview.matchCount > preview.sampleCodes.length ? ' and more' : '';
    const unitsWord = preview.matchCount === 1 ? 'unit page' : 'unit pages';
    return `Shows on ${preview.matchCount} ${unitsWord}, e.g. ${sample}${more}. Shown as "${preview.scopeLabel}".`;
  }

  save(publishAfter: boolean): void {
    const payload = this.toPayload();
    if (!payload) {
      return;
    }
    this.errorMessage.set(null);
    this.saving.set(true);
    const request$ = this.event
      ? this.eventService.updateEvent(this.clubId, this.event.id, payload)
      : this.eventService.createEvent(this.clubId, payload);
    request$.subscribe({
      next: (saved) => {
        if (publishAfter && saved.status !== 'PUBLISHED' && saved.status !== 'PENDING') {
          this.eventService.publishEvent(this.clubId, saved.id).subscribe({
            next: (published) => this.finish(published),
            error: (err) => {
              // The event is saved; only the publish step needs another go.
              this.saving.set(false);
              this.errorMessage.set(apiErrorMessage(err, 'Saved, but publishing did not go through. Publish it from the list.'));
              this.saved.emit(saved);
            }
          });
          return;
        }
        this.finish(saved);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(apiErrorMessage(err, 'The event could not be saved. Please check the fields and try again.'));
      }
    });
  }

  cancel(): void {
    this.cancelled.emit();
  }

  private finish(event: ClubEventManage): void {
    this.saving.set(false);
    this.saved.emit(event);
  }

  private toPayload(): ClubEventUpsert | null {
    const f = this.form;
    if (!f.title.trim()) {
      this.errorMessage.set('Give the event a title.');
      return null;
    }
    const startsAt = fromPerthInputValue(f.startsAt);
    if (!startsAt) {
      this.errorMessage.set('Choose a start date and time.');
      return null;
    }
    const endsAt = f.endsAt ? fromPerthInputValue(f.endsAt) : null;
    if (f.endsAt && !endsAt) {
      this.errorMessage.set('The end time is not a valid date and time.');
      return null;
    }
    if (endsAt && endsAt <= startsAt) {
      this.errorMessage.set('The end time must be after the start time.');
      return null;
    }
    if (f.recurring && !f.recurrenceNote.trim()) {
      this.errorMessage.set('Say how often a recurring event runs, e.g. "Every Tuesday, weeks 2 to 12".');
      return null;
    }
    if (f.targetMode === 'unit' && !f.unitCode.trim()) {
      this.errorMessage.set('Enter the unit code this event is for, or switch to a rule.');
      return null;
    }
    const isUnit = f.targetMode === 'unit';
    return {
      title: f.title.trim(),
      description: f.description.trim() || null,
      kind: f.kind,
      startsAt,
      endsAt,
      location: f.location.trim() || null,
      online: f.online,
      link: f.link.trim() || null,
      recurring: f.recurring,
      recurrenceNote: f.recurring ? f.recurrenceNote.trim() : null,
      unitCode: isUnit ? f.unitCode.trim().toUpperCase() : null,
      codePrefixes: isUnit ? null : f.codePrefixes.trim() || null,
      faculty: isUnit ? null : f.faculty || null,
      level: isUnit ? null : f.level || null,
      showOnHome: f.showOnHome
    };
  }
}
