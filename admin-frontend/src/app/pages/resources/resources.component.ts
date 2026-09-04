import { Component, computed, HostListener, inject, OnInit, signal } from '@angular/core';
import { DatePipe, LowerCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ResourceAdminService } from '../../services/resource-admin.service';
import {
  ResourceOptions,
  ResourcePreview,
  ResourceStatus,
  UnitResourceAdmin,
  UnitResourceUpsert
} from '../../models/resource-admin.model';

type TargetMode = 'unit' | 'rule';

/**
 * The editable fields of a resource, shared by the create form and the edit
 * dialog. Kept as a plain object so both forms bind with ngModel and a reset
 * is one assignment.
 */
interface ResourceForm {
  title: string;
  url: string;
  description: string;
  kind: string;
  targetMode: TargetMode;
  unitCode: string;
  codePrefixes: string;
  faculty: string;
  level: string;
  sortOrder: number;
}

function emptyForm(): ResourceForm {
  return {
    title: '',
    url: '',
    description: '',
    kind: 'WEBSITE',
    targetMode: 'rule',
    unitCode: '',
    codePrefixes: '',
    faculty: '',
    level: '',
    sortOrder: 0
  };
}

function formFrom(row: UnitResourceAdmin): ResourceForm {
  return {
    title: row.title,
    url: row.url,
    description: row.description ?? '',
    kind: row.kind,
    targetMode: row.targetUnitCode ? 'unit' : 'rule',
    unitCode: row.targetUnitCode ?? '',
    codePrefixes: row.codePrefixes ?? '',
    faculty: row.faculty ?? '',
    level: row.level ?? '',
    sortOrder: row.sortOrder
  };
}

@Component({
  selector: 'app-resources',
  imports: [FormsModule, DatePipe, LowerCasePipe],
  templateUrl: './resources.component.html',
  styleUrl: './resources.component.css'
})
export class ResourcesComponent implements OnInit {
  private resourceService = inject(ResourceAdminService);

  options = signal<ResourceOptions>({ kinds: [], faculties: [], levels: [] });
  rows = signal<UnitResourceAdmin[]>([]);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  pending = computed(() => this.rows().filter((r) => r.status === 'PENDING'));

  statusFilter = signal<ResourceStatus | 'ALL'>('ALL');
  kindFilter = signal('ALL');
  search = signal('');
  filteredRows = computed(() => {
    const status = this.statusFilter();
    const kind = this.kindFilter();
    const needle = this.search().trim().toLowerCase();
    return this.rows().filter((r) => {
      if (status !== 'ALL' && r.status !== status) return false;
      if (kind !== 'ALL' && r.kind !== kind) return false;
      if (!needle) return true;
      return (
        r.title.toLowerCase().includes(needle) ||
        r.url.toLowerCase().includes(needle) ||
        r.scopeLabel.toLowerCase().includes(needle) ||
        (r.targetUnitCode ?? '').toLowerCase().includes(needle) ||
        (r.codePrefixes ?? '').toLowerCase().includes(needle)
      );
    });
  });

  // Create form
  createForm: ResourceForm = emptyForm();
  createPreview = signal<ResourcePreview | null>(null);
  createPreviewLoading = signal(false);
  creating = signal(false);

  // Edit dialog. editing holds the row being edited (null = closed).
  editing = signal<UnitResourceAdmin | null>(null);
  editForm: ResourceForm = emptyForm();
  editPreview = signal<ResourcePreview | null>(null);
  editPreviewLoading = signal(false);
  saving = signal(false);
  // When the edit dialog was opened from the pending queue, saving also approves.
  approveAfterSave = signal(false);

  ngOnInit(): void {
    this.resourceService.options().subscribe({
      next: (opts) => this.options.set(opts),
      error: () => this.errorMessage.set('Failed to load form options.')
    });
    this.refresh();
  }

  refresh(): void {
    this.resourceService.list().subscribe({
      next: (rows) => this.rows.set(rows),
      error: () => this.errorMessage.set('Failed to load resources.')
    });
  }

  // Queue actions

  approve(row: UnitResourceAdmin): void {
    this.clearMessages();
    this.resourceService.approve(row.id).subscribe({
      next: (updated) => {
        this.successMessage.set(`Approved "${updated.title}". It is live on matching unit pages.`);
        this.replaceRow(updated);
      },
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to approve resource.')
    });
  }

  reject(row: UnitResourceAdmin): void {
    if (!confirm(`Reject "${row.title}"? It stays in the list as rejected and never shows on the site.`)) return;
    this.clearMessages();
    this.resourceService.reject(row.id).subscribe({
      next: (updated) => {
        this.successMessage.set(`Rejected "${updated.title}".`);
        this.replaceRow(updated);
      },
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to reject resource.')
    });
  }

  delete(row: UnitResourceAdmin): void {
    if (!confirm(`Delete "${row.title}"? This cannot be undone.`)) return;
    this.clearMessages();
    this.resourceService.delete(row.id).subscribe({
      next: () => {
        this.successMessage.set('Resource deleted.');
        this.rows.update((rows) => rows.filter((r) => r.id !== row.id));
      },
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to delete resource.')
    });
  }

  // Create

  previewCreate(): void {
    this.runPreview(this.createForm, this.createPreview, this.createPreviewLoading);
  }

  create(): void {
    this.clearMessages();
    const payload = this.toPayload(this.createForm);
    if (!payload) return;
    this.creating.set(true);
    this.resourceService.create(payload).subscribe({
      next: (created) => {
        this.creating.set(false);
        this.successMessage.set(`Created "${created.title}" (${created.scopeLabel}).`);
        this.rows.update((rows) => [created, ...rows]);
        this.createForm = emptyForm();
        this.createPreview.set(null);
      },
      error: (err) => {
        this.creating.set(false);
        this.errorMessage.set(err.error?.error || 'Failed to create resource.');
      }
    });
  }

  resetCreate(): void {
    this.createForm = emptyForm();
    this.createPreview.set(null);
  }

  // Edit

  openEdit(row: UnitResourceAdmin, approveAfterSave = false): void {
    this.editForm = formFrom(row);
    this.editPreview.set(null);
    this.approveAfterSave.set(approveAfterSave);
    this.editing.set(row);
  }

  closeEdit(): void {
    this.editing.set(null);
    this.approveAfterSave.set(false);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.editing()) this.closeEdit();
  }

  previewEdit(): void {
    this.runPreview(this.editForm, this.editPreview, this.editPreviewLoading);
  }

  saveEdit(): void {
    const row = this.editing();
    if (!row) return;
    this.clearMessages();
    const payload = this.toPayload(this.editForm);
    if (!payload) return;
    this.saving.set(true);
    this.resourceService.update(row.id, payload).subscribe({
      next: (updated) => {
        if (this.approveAfterSave() && updated.status !== 'APPROVED') {
          this.resourceService.approve(updated.id).subscribe({
            next: (approved) => this.finishEdit(approved, `Saved and approved "${approved.title}".`),
            error: (err) => {
              this.saving.set(false);
              this.replaceRow(updated);
              this.errorMessage.set(err.error?.error || 'Saved, but approving failed.');
            }
          });
          return;
        }
        this.finishEdit(updated, `Saved "${updated.title}".`);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(err.error?.error || 'Failed to save resource.');
      }
    });
  }

  private finishEdit(updated: UnitResourceAdmin, message: string): void {
    this.saving.set(false);
    this.replaceRow(updated);
    this.successMessage.set(message);
    this.closeEdit();
  }

  // Sort order: quick nudge from the table, persisted immediately.

  nudgeSort(row: UnitResourceAdmin, delta: number): void {
    const sortOrder = row.sortOrder + delta;
    this.resourceService.reorder([{ id: row.id, sortOrder }]).subscribe({
      next: () => this.replaceRow({ ...row, sortOrder }),
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to update sort order.')
    });
  }

  // Display helpers

  statusClass(status: ResourceStatus): string {
    switch (status) {
      case 'APPROVED': return 'badge badge-active';
      case 'PENDING': return 'badge badge-pending';
      default: return 'badge badge-banned';
    }
  }

  submitterLabel(row: UnitResourceAdmin): string {
    if (row.submittedBy === 'student') return 'Student suggestion';
    if (row.submittedBy === 'admin') return 'Added by an admin';
    return 'Submitter account removed';
  }

  previewSummary(preview: ResourcePreview | null): string {
    if (!preview) return '';
    if (preview.matchCount === 0) return 'Matches no units. Check the prefixes or unit code.';
    const sample = preview.sampleCodes.join(', ');
    const more = preview.matchCount > preview.sampleCodes.length ? ' ...' : '';
    const unitsWord = preview.matchCount === 1 ? 'unit' : 'units';
    return `Matches ${preview.matchCount} ${unitsWord}, e.g. ${sample}${more}`;
  }

  // Internals

  private runPreview(
    form: ResourceForm,
    target: { set(value: ResourcePreview | null): void },
    loading: { set(value: boolean): void }
  ): void {
    this.clearMessages();
    loading.set(true);
    const rule = form.targetMode === 'unit'
      ? { unitCode: form.unitCode.trim() }
      : { codePrefixes: form.codePrefixes.trim(), faculty: form.faculty, level: form.level };
    this.resourceService.preview(rule).subscribe({
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

  private toPayload(form: ResourceForm): UnitResourceUpsert | null {
    if (!form.title.trim()) {
      this.errorMessage.set('A title is required.');
      return null;
    }
    if (!form.url.trim()) {
      this.errorMessage.set('A link is required.');
      return null;
    }
    if (form.targetMode === 'unit' && !form.unitCode.trim()) {
      this.errorMessage.set('Enter the unit code this resource belongs to, or switch to a rule.');
      return null;
    }
    const isUnit = form.targetMode === 'unit';
    return {
      title: form.title.trim(),
      url: form.url.trim(),
      description: form.description.trim() || null,
      kind: form.kind,
      unitCode: isUnit ? form.unitCode.trim().toUpperCase() : null,
      codePrefixes: isUnit ? null : form.codePrefixes.trim() || null,
      faculty: isUnit ? null : form.faculty || null,
      level: isUnit ? null : form.level || null,
      sortOrder: Number.isFinite(form.sortOrder) ? form.sortOrder : 0
    };
  }

  private replaceRow(updated: UnitResourceAdmin): void {
    this.rows.update((rows) => rows.map((r) => (r.id === updated.id ? updated : r)));
  }

  private clearMessages(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
