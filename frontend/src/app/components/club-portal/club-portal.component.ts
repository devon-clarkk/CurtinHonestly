import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ClubEventService } from '../../services/club-event.service';
import { SeoService } from '../../services/seo.service';
import { AuthService } from '../../services/auth.service';
import {
  CLUB_EVENT_LIMITS,
  ClubEventManage,
  ClubEventOptions,
  ClubEventStatus,
  ClubPortalClub,
  ClubProfileUpdate
} from '../../models/club-event.model';
import { apiErrorMessage } from '../boards/board-time.util';
import { formatPerthDateTime } from '../../utils/perth-time.util';
import { EventEditorComponent } from './event-editor/event-editor.component';

interface ProfileForm {
  description: string;
  websiteUrl: string;
  logoUrl: string;
  contactEmail: string;
}

/**
 * /club: the portal for club accounts. Pick a club (when the account belongs
 * to more than one), edit its profile (owners), and create, publish, cancel
 * and edit its events. Trusted clubs publish straight to the site; other
 * clubs' events wait for an admin, which the page says up front.
 */
@Component({
  selector: 'app-club-portal',
  standalone: true,
  imports: [FormsModule, RouterLink, EventEditorComponent],
  templateUrl: './club-portal.component.html',
  styleUrls: ['../events/events.css', './club-portal.component.css']
})
export class ClubPortalComponent implements OnInit {
  private eventService = inject(ClubEventService);
  private seoService = inject(SeoService);
  authService = inject(AuthService);

  readonly limits = CLUB_EVENT_LIMITS;

  clubs = signal<ClubPortalClub[]>([]);
  selectedClubId = signal<string | null>(null);
  club = computed(() => this.clubs().find((c) => c.id === this.selectedClubId()) ?? null);
  canEditProfile = computed(() => {
    const role = this.club()?.role;
    return role === 'OWNER' || role === 'ADMIN';
  });

  options = signal<ClubEventOptions | null>(null);
  events = signal<ClubEventManage[]>([]);
  eventsLoading = signal(false);

  loading = signal(true);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  profileForm: ProfileForm = { description: '', websiteUrl: '', logoUrl: '', contactEmail: '' };
  profileOpen = signal(false);
  profileSaving = signal(false);

  editorOpen = signal(false);
  editingEvent = signal<ClubEventManage | null>(null);

  ngOnInit(): void {
    this.seoService.noIndex('My club | CurtinHonestly');
    this.eventService.options().subscribe({
      next: (options) => this.options.set(options),
      error: () => this.options.set(null)
    });
    this.eventService.myClubs().subscribe({
      next: (clubs) => {
        this.clubs.set(clubs);
        this.loading.set(false);
        if (clubs.length > 0) {
          this.selectClub(clubs[0].id);
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(apiErrorMessage(err, 'Your clubs could not be loaded. Please try again shortly.'));
      }
    });
  }

  selectClub(clubId: string): void {
    this.selectedClubId.set(clubId);
    this.closeEditor();
    this.profileOpen.set(false);
    this.clearMessages();
    const club = this.club();
    if (club) {
      this.profileForm = {
        description: club.description ?? '',
        websiteUrl: club.websiteUrl ?? '',
        logoUrl: club.logoUrl ?? '',
        contactEmail: club.contactEmail ?? ''
      };
    }
    this.loadEvents();
  }

  loadEvents(): void {
    const clubId = this.selectedClubId();
    if (!clubId) {
      return;
    }
    this.eventsLoading.set(true);
    this.eventService.clubEvents(clubId).subscribe({
      next: (events) => {
        this.events.set(events);
        this.eventsLoading.set(false);
      },
      error: (err) => {
        this.eventsLoading.set(false);
        this.errorMessage.set(apiErrorMessage(err, 'Events could not be loaded.'));
      }
    });
  }

  // Profile

  toggleProfile(): void {
    this.profileOpen.update((open) => !open);
    this.clearMessages();
  }

  saveProfile(): void {
    const clubId = this.selectedClubId();
    if (!clubId) {
      return;
    }
    const update: ClubProfileUpdate = {
      description: this.profileForm.description.trim() || null,
      websiteUrl: this.profileForm.websiteUrl.trim() || null,
      logoUrl: this.profileForm.logoUrl.trim() || null,
      contactEmail: this.profileForm.contactEmail.trim() || null
    };
    this.clearMessages();
    this.profileSaving.set(true);
    this.eventService.updateClub(clubId, update).subscribe({
      next: (club) => {
        this.profileSaving.set(false);
        this.clubs.update((clubs) => clubs.map((c) => (c.id === club.id ? club : c)));
        this.profileOpen.set(false);
        this.successMessage.set('Club profile saved.');
      },
      error: (err) => {
        this.profileSaving.set(false);
        this.errorMessage.set(apiErrorMessage(err, 'The profile could not be saved.'));
      }
    });
  }

  // Events

  openNew(): void {
    this.editingEvent.set(null);
    this.editorOpen.set(true);
    this.clearMessages();
  }

  openEdit(event: ClubEventManage): void {
    this.editingEvent.set(event);
    this.editorOpen.set(true);
    this.clearMessages();
  }

  closeEditor(): void {
    this.editorOpen.set(false);
    this.editingEvent.set(null);
  }

  onSaved(event: ClubEventManage): void {
    this.replaceOrAdd(event);
    this.closeEditor();
    this.successMessage.set(this.savedMessage(event));
  }

  publish(event: ClubEventManage): void {
    const clubId = this.selectedClubId();
    if (!clubId) {
      return;
    }
    this.clearMessages();
    this.eventService.publishEvent(clubId, event.id).subscribe({
      next: (updated) => {
        this.replaceOrAdd(updated);
        this.successMessage.set(this.savedMessage(updated));
      },
      error: (err) => this.errorMessage.set(apiErrorMessage(err, 'The event could not be published.'))
    });
  }

  cancel(event: ClubEventManage): void {
    const clubId = this.selectedClubId();
    if (!clubId || !confirm(`Cancel "${event.title}"? It comes off the site straight away.`)) {
      return;
    }
    this.clearMessages();
    this.eventService.cancelEvent(clubId, event.id).subscribe({
      next: (updated) => {
        this.replaceOrAdd(updated);
        this.successMessage.set(`Cancelled "${updated.title}".`);
      },
      error: (err) => this.errorMessage.set(apiErrorMessage(err, 'The event could not be cancelled.'))
    });
  }

  remove(event: ClubEventManage): void {
    const clubId = this.selectedClubId();
    if (!clubId || !confirm(`Delete the draft "${event.title}"? This cannot be undone.`)) {
      return;
    }
    this.clearMessages();
    this.eventService.deleteEvent(clubId, event.id).subscribe({
      next: () => {
        this.events.update((events) => events.filter((e) => e.id !== event.id));
        this.successMessage.set('Draft deleted.');
      },
      error: (err) => this.errorMessage.set(apiErrorMessage(err, 'The draft could not be deleted.'))
    });
  }

  // Display helpers

  when(event: ClubEventManage): string {
    return formatPerthDateTime(event.startsAt, true);
  }

  canPublish(event: ClubEventManage): boolean {
    return event.status === 'DRAFT' || event.status === 'REJECTED' || event.status === 'CANCELLED';
  }

  canCancel(event: ClubEventManage): boolean {
    return event.status === 'PUBLISHED' || event.status === 'PENDING';
  }

  statusClass(status: ClubEventStatus): string {
    switch (status) {
      case 'PUBLISHED': return 'portal-status live';
      case 'PENDING': return 'portal-status pending';
      case 'REJECTED': return 'portal-status rejected';
      case 'CANCELLED': return 'portal-status cancelled';
      default: return 'portal-status';
    }
  }

  roleLabel(role: string): string {
    switch (role) {
      case 'OWNER': return 'Owner';
      case 'EDITOR': return 'Editor';
      case 'ADMIN': return 'Site admin';
      default: return role;
    }
  }

  private savedMessage(event: ClubEventManage): string {
    switch (event.status) {
      case 'PUBLISHED': return `"${event.title}" is live on the site.`;
      case 'PENDING': return `"${event.title}" is with the CurtinHonestly admins for approval.`;
      default: return `Saved "${event.title}" as a ${event.statusLabel.toLowerCase()}.`;
    }
  }

  private replaceOrAdd(event: ClubEventManage): void {
    this.events.update((events) => {
      const exists = events.some((e) => e.id === event.id);
      return exists ? events.map((e) => (e.id === event.id ? event : e)) : [event, ...events];
    });
  }

  private clearMessages(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
