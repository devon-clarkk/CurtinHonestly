// Unit resources: curated links shown on unit pages (Discord servers, clubs,
// textbooks, past papers). Managed at /admin/unit-resources.

export type ResourceStatus = 'APPROVED' | 'PENDING' | 'REJECTED';

export interface ResourceOption {
  value: string;
  label: string;
}

export interface ResourceOptions {
  kinds: ResourceOption[];
  faculties: ResourceOption[];
  levels: ResourceOption[];
}

export interface UnitResourceAdmin {
  id: string;
  title: string;
  url: string;
  description: string | null;
  kind: string;
  kindLabel: string;
  targetUnitCode: string | null;
  targetUnitName: string | null;
  codePrefixes: string | null;
  faculty: string | null;
  facultyLabel: string | null;
  level: string | null;
  levelLabel: string | null;
  scopeLabel: string;
  status: ResourceStatus;
  sortOrder: number;
  clickCount: number;
  /** "student", "admin" or null when the submitting account no longer exists. */
  submittedBy: string | null;
  submitterNote: string | null;
  createdAt: string;
  approvedAt: string | null;
}

/**
 * Create or edit payload. A non-empty unitCode targets that one unit and the
 * rule fields are ignored; otherwise the row is a rule built from the prefix
 * list, faculty and level (all optional; none set means every unit).
 */
export interface UnitResourceUpsert {
  title: string;
  url: string;
  description: string | null;
  kind: string;
  unitCode: string | null;
  codePrefixes: string | null;
  faculty: string | null;
  level: string | null;
  status?: ResourceStatus;
  sortOrder?: number;
}

export interface ResourcePreview {
  matchCount: number;
  sampleCodes: string[];
  scopeLabel: string;
}

export interface ResourceReorderItem {
  id: string;
  sortOrder: number;
}
