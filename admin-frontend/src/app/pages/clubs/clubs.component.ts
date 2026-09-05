import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ClubAdminService } from '../../services/club-admin.service';
import {
  AdminClub,
  AdminClubUpsert,
  ClubEventManage,
  ClubEventOptions,
  ClubEventPreview,
  ClubEventStatus,
  ClubEventUpsert
} from '../../models/club-admin.model';
import { formatPerthDateTime, fromPerthInputValue, toPerthInputValue } from '../../utils/perth-time';

type TargetMode = 'unit' | 'rule';

interface ClubForm {
  name: string;
  slug: string;
  description: string;
  websiteUrl: string;
  logoUrl: string;
  contactEmail: string;
  trusted: boolean;
  active: boolean;
}

function emptyClubForm(): ClubForm {
  return { name: '', slug: '', description: '', websiteUrl: '', logoUrl: '', contactEmail: '', trusted: false, active: true };
}

function clubFormFrom(club: AdminClub): ClubForm {
  return {
    name: club.name,
    slug: club.slug,
    description: club.description ?? '',
    websiteUrl: club.websiteUrl ?? '',
    logoUrl: club.logoUrl ?? '',
    contactEmail: club.contactEmail ?? '',
    trusted: club.trusted,
    active: club.active
  };
}

interface EventForm {
  clubId: string;
  title: string;
  description: string;
  kind: string;
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

function emptyEventForm(clubId = ''): EventForm {
  return {
    clubId,
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

function eventFormFrom(row: ClubEventManage): EventForm {
  return {
    clubId: row.clubId,
    title: row.title,
    description: row.description ?? '',
    kind: row.kind,
    startsAt: toPerthInputValue(row.startsAt),
    endsAt: toPerthInputValue(row.endsAt),
    online: row.online,
    location: row.location ?? '',
    link: row.link ?? '',
    recurring: row.recurring,
    recurrenceNote: row.recurrenceNote ?? '',
    targetMode: row.targetUnitCode ? 'unit' : 'rule',
    unitCode: row.targetUnitCode ?? '',
    codePrefixes: row.codePrefixes ?? '',
    faculty: row.faculty ?? '',
    level: row.level ?? '',
    showOnHome: row.showOnHome
  };
}

/** The outreach blurb for a club that has no account yet. Copied to the clipboard from the club card. */
export function inviteText(clubName: string): string {
  return `We would like to list ${clubName}'s study events on CurtinHonestly. Sign up at https://www.curtinhonestly.com/register with the email you want us to link, then reply and we will grant your account club access.`;
}

@Component({
  selector: 'app-clubs',
  imports: [FormsModule, LowerCasePipe],
  templateUrl: './clubs.component.html',
  styleUrl: './clubs.component.css'
})
export class ClubsComponent implements OnInit {
  private clubService = inject(ClubAdminService);

  clubs = signal<AdminClub[]>([]);
  events = signal<ClubEventManage[]>([]);
  options = signal<ClubEventOptions>({ kinds: [], statuses: [], faculties: [], levels: [] });
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  pending = computed(() => this.events().filter((e) => e.status === 'PENDING'));

  // Event table filters
  statusFilter = signal<ClubEventStatus | 'ALL'>('ALL');
  clubFilter = signal('ALL');
  search = signal('');
  filteredEvents = computed(() => {
    const status = this.statusFilter();
    const club = this.clubFilter();
    const needle = this.search().trim().toLowerCase();
    return this.events().filter((e) => {
      if (status !== 'ALL' && e.status !== status) return false;
      if (club !== 'ALL' && e.clubId !== club) return false;
      if (!needle) return true;
      return (
        e.title.toLowerCase().includes(needle) ||
        e.clubName.toLowerCase().includes(needle) ||
        e.scopeLabel.toLowerCase().includes(needle) ||
        (e.targetUnitCode ?? '').toLowerCase().includes(needle) ||
        (e.codePrefixes ?? '').toLowerCase().includes(needle)
      );
    });
  });

  // Club create form and edit dialog
  clubForm: ClubForm = emptyClubForm();
  creatingClub = signal(false);
  editingClub = signal<AdminClub | null>(null);
  editClubForm: ClubForm = emptyClubForm();
  savingClub = signal(false);

  // Member add forms, keyed by club id so several cards can be open at once.
  memberEmail: Record<string, string> = {};
  memberRole: Record<string, string> = {};
  copiedClubId = signal<string | null>(null);

  // Event create form and edit dialog
  eventForm: EventForm = emptyEventForm();
  eventPreview = signal<ClubEventPreview | null>(null);
  eventPreviewLoading = signal(false);
  creatingEvent = signal(false);
  editingEvent = signal<ClubEventManage | null>(null);
  editEventForm: EventForm = emptyEventForm();
  editEventPreview = signal<ClubEventPreview | null>(null);
  editEventPreviewLoading = signal(false);
  savingEvent = signal(false);
  approveAfterSave = signal(false);

  ngOnInit(): void {
    this.clubService.options().subscribe({
      next: (opts) => this.options.set(opts),
      error: () => this.errorMessage.set('Failed to load form options.')
    });
    this.refresh();
  }

  refresh(): void {
    this.clubService.listClubs().subscribe({
      next: (clubs) => this.clubs.set(clubs),
      error: () => this.errorMessage.set('Failed to load clubs.')
    });
    this.clubService.listEvents().subscribe({
      next: (events) => this.events.set(events),
      error: () => this.errorMessage.set('Failed to load events.')
    });
  }

  // ------------------------------------------------------------------ clubs

  createClub(): void {
    this.clearMessages();
    const payload = this.toClubPayload(this.clubForm);
    if (!payload) return;
    this.creatingClub.set(true);
    this.clubService.createClub(payload).subscribe({
      next: (club) => {
        this.creatingClub.set(false);
        this.clubs.update((clubs) => [...clubs, club].sort((a, b) => a.name.localeCompare(b.name)));
        this.clubForm = emptyClubForm();
        this.successMessage.set(`Created ${club.name}. Add its members below, or copy the invite text if they have no account yet.`);
      },
      error: (err) => {
        this.creatingClub.set(false);
        this.errorMessage.set(err.error?.error || 'Failed to create club.');
      }
    });
  }

  openEditClub(club: AdminClub): void {
    this.editClubForm = clubFormFrom(club);
    this.editingClub.set(club);
  }

  closeEditClub(): void {
    this.editingClub.set(null);
  }

  saveClub(): void {
    const club = this.editingClub();
    if (!club) return;
    this.clearMessages();
    const payload = this.toClubPayload(this.editClubForm);
    if (!payload) return;
    this.savingClub.set(true);
    this.clubService.updateClub(club.id, payload).subscribe({
      next: (updated) => {
        this.savingClub.set(false);
        this.replaceClub(updated);
        this.closeEditClub();
        this.successMessage.set(`Saved ${updated.name}.`);
        // Trust and active flags change what the public site shows, so reload events too.
        this.clubService.listEvents().subscribe({ next: (events) => this.events.set(events) });
      },
      error: (err) => {
        this.savingClub.set(false);
        this.errorMessage.set(err.error?.error || 'Failed to save club.');
      }
    });
  }

  deleteClub(club: AdminClub): void {
    if (!confirm(`Delete ${club.name}, its ${club.members.length} member link(s) and its ${club.eventCount} event(s)? This cannot be undone.`)) return;
    this.clearMessages();
    this.clubService.deleteClub(club.id).subscribe({
      next: () => {
        this.clubs.update((clubs) => clubs.filter((c) => c.id !== club.id));
        this.events.update((events) => events.filter((e) => e.clubId !== club.id));
        this.closeEditClub();
        this.successMessage.set(`Deleted ${club.name}.`);
      },
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to delete club.')
    });
  }

  // ---------------------------------------------------------------- members

  addMember(club: AdminClub): void {
    const email = (this.memberEmail[club.id] ?? '').trim();
    if (!email) {
      this.errorMessage.set('Enter the email of an existing account.');
      return;
    }
    this.clearMessages();
    this.clubService.addMember(club.id, { email, role: this.memberRole[club.id] || 'EDITOR' }).subscribe({
      next: (updated) => {
        this.replaceClub(updated);
        this.memberEmail[club.id] = '';
        this.successMessage.set(`Added ${email} to ${updated.name}. Their account now has club access.`);
      },
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to add member.')
    });
  }

  setMemberRole(club: AdminClub, userId: string, role: string): void {
    this.clearMessages();
    this.clubService.setMemberRole(club.id, userId, role).subscribe({
      next: (updated) => this.replaceClub(updated),
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to change role.')
    });
  }

  removeMember(club: AdminClub, userId: string, email: string): void {
    if (!confirm(`Remove ${email} from ${club.name}? If this was their only club, their account loses club access.`)) return;
    this.clearMessages();
    this.clubService.removeMember(club.id, userId).subscribe({
      next: (updated) => {
        this.replaceClub(updated);
        this.successMessage.set(`Removed ${email} from ${updated.name}.`);
      },
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to remove member.')
    });
  }

  inviteText(club: AdminClub): string {
    return inviteText(club.name);
  }

  copyInvite(club: AdminClub): void {
    const text = inviteText(club.name);
    const clipboard = typeof navigator !== 'undefined' ? navigator.clipboard : undefined;
    if (!clipboard) {
      this.errorMessage.set('Clipboard access is not available here. Copy the text from the club card instead.');
      return;
    }
    clipboard.writeText(text).then(
      () => {
        this.copiedClubId.set(club.id);
        setTimeout(() => {
          if (this.copiedClubId() === club.id) this.copiedClubId.set(null);
        }, 2500);
      },
      () => this.errorMessage.set('Copying failed. Copy the text from the club card instead.')
    );
  }

  // ----------------------------------------------------------------- events

  approve(row: ClubEventManage): void {
    this.clearMessages();
    this.clubService.approveEvent(row.id).subscribe({
      next: (updated) => {
        this.replaceEvent(updated);
        this.successMessage.set(`Approved "${updated.title}". It is live on the site.`);
      },
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to approve event.')
    });
  }

  reject(row: ClubEventManage): void {
    const reason = prompt(`Why is "${row.title}" being rejected? The club sees this in its portal.`);
    if (reason === null) return;
    this.clearMessages();
    this.clubService.rejectEvent(row.id, reason).subscribe({
      next: (updated) => {
        this.replaceEvent(updated);
        this.successMessage.set(`Rejected "${updated.title}".`);
      },
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to reject event.')
    });
  }

  cancel(row: ClubEventManage): void {
    if (!confirm(`Cancel "${row.title}"? It comes off the site straight away.`)) return;
    this.clearMessages();
    this.clubService.cancelEvent(row.id).subscribe({
      next: (updated) => {
        this.replaceEvent(updated);
        this.successMessage.set(`Cancelled "${updated.title}".`);
      },
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to cancel event.')
    });
  }

  deleteEvent(row: ClubEventManage): void {
    if (!confirm(`Delete "${row.title}"? This cannot be undone.`)) return;
    this.clearMessages();
    this.clubService.deleteEvent(row.id).subscribe({
      next: () => {
        this.events.update((events) => events.filter((e) => e.id !== row.id));
        this.closeEditEvent();
        this.successMessage.set('Event deleted.');
      },
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to delete event.')
    });
  }

  previewCreateEvent(): void {
    this.runPreview(this.eventForm, this.eventPreview, this.eventPreviewLoading);
  }

  createEvent(): void {
    this.clearMessages();
    if (!this.eventForm.clubId) {
      this.errorMessage.set('Choose the club this event belongs to.');
      return;
    }
    const payload = this.toEventPayload(this.eventForm);
    if (!payload) return;
    this.creatingEvent.set(true);
    this.clubService.createEvent(this.eventForm.clubId, payload).subscribe({
      next: (created) => {
        this.creatingEvent.set(false);
        this.events.update((events) => [created, ...events]);
        this.eventForm = emptyEventForm();
        this.eventPreview.set(null);
        this.successMessage.set(`Published "${created.title}" for ${created.clubName}.`);
        this.refreshClubCounts();
      },
      error: (err) => {
        this.creatingEvent.set(false);
        this.errorMessage.set(err.error?.error || 'Failed to create event.');
      }
    });
  }

  resetEventForm(): void {
    this.eventForm = emptyEventForm();
    this.eventPreview.set(null);
  }

  openEditEvent(row: ClubEventManage, approveAfterSave = false): void {
    this.editEventForm = eventFormFrom(row);
    this.editEventPreview.set(null);
    this.approveAfterSave.set(approveAfterSave);
    this.editingEvent.set(row);
  }

  closeEditEvent(): void {
    this.editingEvent.set(null);
    this.approveAfterSave.set(false);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.editingEvent()) this.closeEditEvent();
    else if (this.editingClub()) this.closeEditClub();
  }

  previewEditEvent(): void {
    this.runPreview(this.editEventForm, this.editEventPreview, this.editEventPreviewLoading);
  }

  saveEvent(): void {
    const row = this.editingEvent();
    if (!row) return;
    this.clearMessages();
    const payload = this.toEventPayload(this.editEventForm);
    if (!payload) return;
    this.savingEvent.set(true);
    this.clubService.updateEvent(row.id, payload).subscribe({
      next: (updated) => {
        if (this.approveAfterSave() && updated.status === 'PENDING') {
          this.clubService.approveEvent(updated.id).subscribe({
            next: (approved) => this.finishEventEdit(approved, `Saved and approved "${approved.title}".`),
            error: (err) => {
              this.savingEvent.set(false);
              this.replaceEvent(updated);
              this.errorMessage.set(err.error?.error || 'Saved, but approving failed.');
            }
          });
          return;
        }
        this.finishEventEdit(updated, `Saved "${updated.title}".`);
      },
      error: (err) => {
        this.savingEvent.set(false);
        this.errorMessage.set(err.error?.error || 'Failed to save event.');
      }
    });
  }

  private finishEventEdit(updated: ClubEventManage, message: string): void {
    this.savingEvent.set(false);
    this.replaceEvent(updated);
    this.successMessage.set(message);
    this.closeEditEvent();
  }

  // -------------------------------------------------------- display helpers

  when(row: ClubEventManage): string {
    return formatPerthDateTime(row.startsAt);
  }

  statusClass(status: ClubEventStatus): string {
    switch (status) {
      case 'PUBLISHED': return 'badge badge-active';
      case 'PENDING': return 'badge badge-pending';
      case 'REJECTED': return 'badge badge-banned';
      case 'CANCELLED': return 'badge badge-cancelled';
      default: return 'badge badge-draft';
    }
  }

  previewSummary(preview: ClubEventPreview | null): string {
    if (!preview) return '';
    if (preview.matchCount === 0) return 'Matches no units. Check the prefixes or unit code.';
    const sample = preview.sampleCodes.join(', ');
    const more = preview.matchCount > preview.sampleCodes.length ? ' ...' : '';
    const unitsWord = preview.matchCount === 1 ? 'unit' : 'units';
    return `Matches ${preview.matchCount} ${unitsWord}, e.g. ${sample}${more}`;
  }

  // --------------------------------------------------------------- internals

  private runPreview(
    form: EventForm,
    target: { set(value: ClubEventPreview | null): void },
    loading: { set(value: boolean): void }
  ): void {
    this.clearMessages();
    loading.set(true);
    const rule = form.targetMode === 'unit'
      ? { unitCode: form.unitCode.trim() }
      : { codePrefixes: form.codePrefixes.trim(), faculty: form.faculty, level: form.level };
    this.clubService.preview(rule).subscribe({
      next: (preview) => {
        loading.set(false);
        target.set(preview);
      },
      error: (err) => {
        loading.set(false);
        target.set(null);
        this.errorMessage.set(err.error?.error || 'Preview failed.');
      }
    });
  }

  private toClubPayload(form: ClubForm): AdminClubUpsert | null {
    if (!form.name.trim()) {
      this.errorMessage.set('A club name is required.');
      return null;
    }
    return {
      name: form.name.trim(),
      slug: form.slug.trim() || null,
      description: form.description.trim() || null,
      websiteUrl: form.websiteUrl.trim() || null,
      logoUrl: form.logoUrl.trim() || null,
      contactEmail: form.contactEmail.trim() || null,
      trusted: form.trusted,
      active: form.active
    };
  }

  private toEventPayload(form: EventForm): ClubEventUpsert | null {
    if (!form.title.trim()) {
      this.errorMessage.set('A title is required.');
      return null;
    }
    const startsAt = fromPerthInputValue(form.startsAt);
    if (!startsAt) {
      this.errorMessage.set('A start date and time is required.');
      return null;
    }
    const endsAt = form.endsAt ? fromPerthInputValue(form.endsAt) : null;
    if (endsAt && endsAt <= startsAt) {
      this.errorMessage.set('The end time must be after the start time.');
      return null;
    }
    if (form.recurring && !form.recurrenceNote.trim()) {
      this.errorMessage.set('Say how often a recurring event runs, e.g. "Every Tuesday, weeks 2 to 12".');
      return null;
    }
    if (form.targetMode === 'unit' && !form.unitCode.trim()) {
      this.errorMessage.set('Enter the unit code this event is for, or switch to a rule.');
      return null;
    }
    const isUnit = form.targetMode === 'unit';
    return {
      title: form.title.trim(),
      description: form.description.trim() || null,
      kind: form.kind,
      startsAt,
      endsAt,
      location: form.location.trim() || null,
      online: form.online,
      link: form.link.trim() || null,
      recurring: form.recurring,
      recurrenceNote: form.recurring ? form.recurrenceNote.trim() : null,
      unitCode: isUnit ? form.unitCode.trim().toUpperCase() : null,
      codePrefixes: isUnit ? null : form.codePrefixes.trim() || null,
      faculty: isUnit ? null : form.faculty || null,
      level: isUnit ? null : form.level || null,
      showOnHome: form.showOnHome
    };
  }

  private refreshClubCounts(): void {
    this.clubService.listClubs().subscribe({ next: (clubs) => this.clubs.set(clubs) });
  }

  private replaceClub(updated: AdminClub): void {
    this.clubs.update((clubs) => clubs.map((c) => (c.id === updated.id ? updated : c)));
  }

  private replaceEvent(updated: ClubEventManage): void {
    this.events.update((events) => events.map((e) => (e.id === updated.id ? updated : e)));
    this.refreshClubCounts();
  }

  private clearMessages(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
