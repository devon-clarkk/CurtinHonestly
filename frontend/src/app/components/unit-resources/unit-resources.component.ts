import { Component, Input, OnChanges, PLATFORM_ID, SimpleChanges, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UnitResourceService } from '../../services/unit-resource.service';
import { AuthService } from '../../services/auth.service';
import {
  RESOURCE_KIND_LABELS,
  RESOURCE_KIND_ORDER,
  ResourceKind,
  UnitResource,
  UnitResourceSuggestion
} from '../../models/unit-resource.model';

interface ResourceGroup {
  kind: ResourceKind;
  label: string;
  items: UnitResource[];
}

const MAX_TITLE = 120;
const MAX_DESCRIPTION = 300;
const MAX_NOTE = 300;

/**
 * "Resources and communities" card for a unit page: curated links grouped by
 * kind (Discord, clubs, notes, past papers ...), each with a scope chip that
 * says why it is here ("This unit", "All COMP units", "All units").
 *
 * Data is fetched in the browser only. Unit pages are prerendered in bulk and
 * the resource list is not part of the SEO payload, so the server render
 * leaves this card empty rather than making 1,700 extra API calls.
 *
 * Usage: <app-unit-resources [unitCode]="unit.code" [unitName]="unit.name" />
 */
@Component({
  selector: 'app-unit-resources',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './unit-resources.component.html',
  styleUrl: './unit-resources.component.css'
})
export class UnitResourcesComponent implements OnChanges {
  @Input({ required: true }) unitCode!: string;
  @Input() unitName?: string;

  private resourceService = inject(UnitResourceService);
  private platformId = inject(PLATFORM_ID);
  authService = inject(AuthService);

  private readonly isBrowser = isPlatformBrowser(this.platformId);

  items = signal<UnitResource[]>([]);
  loaded = signal(false);

  groups = computed<ResourceGroup[]>(() => {
    const byKind = new Map<ResourceKind, UnitResource[]>();
    for (const item of this.items()) {
      const kind: ResourceKind = item.kind in RESOURCE_KIND_LABELS ? item.kind : 'OTHER';
      const list = byKind.get(kind) ?? [];
      list.push(item);
      byKind.set(kind, list);
    }
    return RESOURCE_KIND_ORDER
      .filter((kind) => byKind.has(kind))
      .map((kind) => ({ kind, label: RESOURCE_KIND_LABELS[kind], items: byKind.get(kind)! }));
  });

  // Suggestion form. Visible to signed-in users, collapsed until asked for.
  readonly kinds = RESOURCE_KIND_ORDER;
  readonly kindLabels = RESOURCE_KIND_LABELS;
  readonly maxTitle = MAX_TITLE;
  readonly maxDescription = MAX_DESCRIPTION;
  readonly maxNote = MAX_NOTE;

  showSuggestForm = signal(false);
  suggestSubmitting = signal(false);
  suggestError = signal<string | null>(null);
  suggestDone = signal(false);

  suggestTitle = '';
  suggestUrl = '';
  suggestKind: ResourceKind = 'WEBSITE';
  suggestDescription = '';
  suggestNote = '';

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['unitCode']) {
      this.resetSuggestForm();
      this.load();
    }
  }

  private load(): void {
    if (!this.isBrowser || !this.unitCode) {
      return;
    }
    this.loaded.set(false);
    this.resourceService.list(this.unitCode).subscribe({
      next: (res) => {
        this.items.set(res?.items ?? []);
        this.loaded.set(true);
      },
      error: () => {
        this.items.set([]);
        this.loaded.set(true);
      }
    });
  }

  /** Counts the click without delaying navigation; the link itself is a plain anchor. */
  recordClick(item: UnitResource): void {
    if (!this.isBrowser) {
      return;
    }
    this.resourceService.click(this.unitCode, item.id).subscribe({
      next: () => undefined,
      error: () => undefined
    });
  }

  /** Short glyph per kind, decorative only (the kind label is always rendered as text). */
  glyph(kind: ResourceKind): string {
    switch (kind) {
      case 'DISCORD': return 'DC';
      case 'CLUB': return 'CL';
      case 'STUDY_GROUP': return 'SG';
      case 'NOTES': return 'NT';
      case 'PAST_PAPERS': return 'PP';
      case 'TEXTBOOK': return 'TB';
      case 'VIDEO': return 'VD';
      case 'WEBSITE': return 'WB';
      default: return 'LK';
    }
  }

  /** The hostname, shown as a quiet hint of where the link goes. */
  host(url: string): string {
    try {
      return new URL(url).hostname.replace(/^www\./, '');
    } catch {
      return '';
    }
  }

  toggleSuggestForm(): void {
    this.showSuggestForm.update((open) => !open);
    this.suggestError.set(null);
  }

  canSubmitSuggestion(): boolean {
    return (
      !this.suggestSubmitting() &&
      this.suggestTitle.trim().length > 0 &&
      this.suggestTitle.trim().length <= MAX_TITLE &&
      this.suggestUrl.trim().length > 0 &&
      this.suggestDescription.length <= MAX_DESCRIPTION &&
      this.suggestNote.length <= MAX_NOTE
    );
  }

  submitSuggestion(): void {
    if (!this.canSubmitSuggestion() || !this.unitCode) {
      return;
    }
    const payload: UnitResourceSuggestion = {
      title: this.suggestTitle.trim(),
      url: this.suggestUrl.trim(),
      kind: this.suggestKind
    };
    if (this.suggestDescription.trim()) {
      payload.description = this.suggestDescription.trim();
    }
    if (this.suggestNote.trim()) {
      payload.note = this.suggestNote.trim();
    }

    this.suggestError.set(null);
    this.suggestSubmitting.set(true);
    this.resourceService.suggest(this.unitCode, payload).subscribe({
      next: () => {
        this.suggestSubmitting.set(false);
        this.suggestDone.set(true);
        this.showSuggestForm.set(false);
      },
      error: (err) => {
        this.suggestSubmitting.set(false);
        this.suggestError.set(err?.error?.error || 'Could not send that suggestion. Please try again.');
      }
    });
  }

  private resetSuggestForm(): void {
    this.showSuggestForm.set(false);
    this.suggestSubmitting.set(false);
    this.suggestError.set(null);
    this.suggestDone.set(false);
    this.suggestTitle = '';
    this.suggestUrl = '';
    this.suggestKind = 'WEBSITE';
    this.suggestDescription = '';
    this.suggestNote = '';
  }
}
